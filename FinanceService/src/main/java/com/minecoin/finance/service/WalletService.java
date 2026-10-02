package com.minecoin.finance.service;

import com.minecoin.finance.domain.Operation;
import com.minecoin.finance.domain.Wallet;
import com.minecoin.finance.domain.exception.AmountLimitExceededException;
import com.minecoin.finance.domain.exception.RecipientNotFoundException;
import com.minecoin.finance.domain.exception.SelfTransferException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
public class WalletService {

    private final WalletRepository walletRepository;
    private final OperationRepository operationRepository;
    private final UserDirectory userDirectory;
    private final TransactionTemplate transactionTemplate;
    private final long maxOperationAmount;

    public WalletService(
            WalletRepository walletRepository,
            OperationRepository operationRepository,
            UserDirectory userDirectory,
            PlatformTransactionManager transactionManager,
            FinanceProperties properties) {
        this.walletRepository = walletRepository;
        this.operationRepository = operationRepository;
        this.userDirectory = userDirectory;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.maxOperationAmount = properties.maxOperationAmount();
    }

    @Transactional
    public Wallet openWallet(UUID userId) {
        ensureWallet(userId);
        return walletRepository.findByUserId(userId).orElseThrow();
    }

    public long maxOperationAmount() {
        return maxOperationAmount;
    }

    @Transactional
    public Wallet deposit(UUID userId, long amount) {
        checkLimit(amount);
        Wallet wallet = lockWallet(userId);
        wallet.deposit(amount);
        operationRepository.save(Operation.deposit(wallet, amount));
        return wallet;
    }

    @Transactional
    public Wallet withdraw(UUID userId, long amount) {
        checkLimit(amount);
        Wallet wallet = lockWallet(userId);
        wallet.withdraw(amount);
        operationRepository.save(Operation.withdraw(wallet, amount));
        return wallet;
    }

    public Wallet transfer(UUID userId, String toUsername, long amount) {
        checkLimit(amount);
        UserRef recipient = userDirectory.findByUsername(toUsername)
                .orElseThrow(() -> new RecipientNotFoundException(toUsername));
        if (recipient.id().equals(userId)) {
            throw new SelfTransferException();
        }
        return transactionTemplate.execute(status -> {
            ensureWallet(userId);
            ensureWallet(recipient.id());
            Map<UUID, Wallet> locked = walletRepository.lockAllByUserIdsOrderedById(List.of(userId, recipient.id()))
                    .stream()
                    .collect(Collectors.toMap(Wallet::getUserId, Function.identity()));
            Wallet from = locked.get(userId);
            Wallet to = locked.get(recipient.id());
            from.withdraw(amount);
            to.deposit(amount);
            operationRepository.save(Operation.transfer(from, to, amount));
            return from;
        });
    }

    public Page<OperationView> history(UUID userId, Pageable pageable) {
        HistoryPage history = transactionTemplate.execute(status -> {
            Wallet wallet = openWallet(userId);
            Page<Operation> operations = operationRepository.findAllByWalletId(wallet.getId(), pageable);
            Set<UUID> counterpartyWalletIds = operations.stream()
                    .map(operation -> counterpartyWalletId(operation, wallet.getId()))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            Map<UUID, UUID> userIdByWalletId = walletRepository.findAllById(counterpartyWalletIds).stream()
                    .collect(Collectors.toMap(Wallet::getId, Wallet::getUserId));
            return new HistoryPage(wallet.getId(), operations, userIdByWalletId);
        });
        Map<UUID, String> usernames = findUsernamesOrEmpty(history.userIdByWalletId().values());
        return history.operations().map(operation -> toView(operation, history, usernames));
    }

    private void ensureWallet(UUID userId) {
        if (!walletRepository.existsByUserId(userId)) {
            walletRepository.insertIfAbsent(userId);
        }
    }

    private Wallet lockWallet(UUID userId) {
        ensureWallet(userId);
        return walletRepository.lockAllByUserIdsOrderedById(List.of(userId)).getFirst();
    }

    private void checkLimit(long amount) {
        if (amount > maxOperationAmount) {
            throw new AmountLimitExceededException(maxOperationAmount);
        }
    }

    private Map<UUID, String> findUsernamesOrEmpty(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        try {
            return userDirectory.findUsernames(userIds);
        } catch (UserDirectoryUnavailableException ex) {
            log.warn("History is returned without usernames: {}", ex.getMessage());
            return Map.of();
        }
    }

    private static UUID counterpartyWalletId(Operation operation, UUID walletId) {
        if (walletId.equals(operation.getFromWalletId())) {
            return operation.getToWalletId();
        }
        return operation.getFromWalletId();
    }

    private static OperationView toView(Operation operation, HistoryPage history, Map<UUID, String> usernames) {
        boolean outgoing = history.walletId().equals(operation.getFromWalletId());
        UUID counterpartyUserId = history.userIdByWalletId().get(counterpartyWalletId(operation, history.walletId()));
        return new OperationView(
                operation.getId(),
                operation.getType(),
                outgoing ? OperationDirection.OUT : OperationDirection.IN,
                operation.getAmount(),
                counterpartyUserId,
                counterpartyUserId == null ? null : usernames.get(counterpartyUserId),
                operation.getCreatedAt());
    }

    private record HistoryPage(UUID walletId, Page<Operation> operations, Map<UUID, UUID> userIdByWalletId) {
    }
}
