package com.minecoin.finance.service;

import static com.minecoin.finance.domain.OperationType.DEPOSIT;
import static com.minecoin.finance.domain.OperationType.TRANSFER;
import static com.minecoin.finance.domain.OperationType.WITHDRAW;

import com.minecoin.finance.domain.Operation;
import com.minecoin.finance.domain.OperationType;
import com.minecoin.finance.domain.Wallet;
import com.minecoin.finance.domain.exception.AmountLimitExceededException;
import com.minecoin.finance.domain.exception.DomainException;
import com.minecoin.finance.domain.exception.RecipientNotFoundException;
import com.minecoin.finance.domain.exception.SelfTransferException;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.*;
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

    private static final String OPERATIONS = "minecoin.wallet.operations";
    private static final String TRANSFERS = "minecoin.wallet.transfers";
    private static final String AMOUNT = "minecoin.wallet.operation.amount";
    private static final String SUCCESS = "success";

    private final WalletRepository walletRepository;
    private final OperationRepository operationRepository;
    private final UserDirectory userDirectory;
    private final TransactionTemplate transactionTemplate;
    private final long maxOperationAmount;
    private final MeterRegistry metrics;

    public WalletService(
            WalletRepository walletRepository,
            OperationRepository operationRepository,
            UserDirectory userDirectory,
            PlatformTransactionManager transactionManager,
            FinanceProperties properties,
            MeterRegistry metrics) {
        this.walletRepository = walletRepository;
        this.operationRepository = operationRepository;
        this.userDirectory = userDirectory;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.maxOperationAmount = properties.maxOperationAmount();
        this.metrics = metrics;
    }

    @Transactional
    public Wallet openWallet(UUID userId) {
        ensureWallet(userId);
        return walletRepository.findByUserId(userId).orElseThrow();
    }

    public long maxOperationAmount() {
        return maxOperationAmount;
    }

    public Wallet deposit(UUID userId, long amount) {
        try {
            Wallet wallet = doSingleOperation(userId, DEPOSIT, amount);
            metrics.counter(OPERATIONS, "type", tag(DEPOSIT), "result", SUCCESS).increment();
            metrics.summary(AMOUNT, "type", tag(DEPOSIT)).record(amount);
            return wallet;
        } catch (DomainException ex) {
            metrics.counter(OPERATIONS, "type", tag(DEPOSIT), "result", result(ex)).increment();
            throw ex;
        }
    }

    public Wallet withdraw(UUID userId, long amount) {
        try {
            Wallet wallet = doSingleOperation(userId, WITHDRAW, amount);
            metrics.counter(OPERATIONS, "type", tag(WITHDRAW), "result", SUCCESS).increment();
            metrics.summary(AMOUNT, "type", tag(WITHDRAW)).record(amount);
            return wallet;
        } catch (DomainException ex) {
            metrics.counter(OPERATIONS, "type", tag(WITHDRAW), "result", result(ex)).increment();
            throw ex;
        }
    }

    private Wallet doSingleOperation(UUID userId, OperationType type, long amount) {
        checkLimit(amount);
        return transactionTemplate.execute(_ -> {
            Wallet wallet = lockWallet(userId);
            Operation operation = switch (type) {
                case DEPOSIT -> {
                    wallet.deposit(amount);
                    yield Operation.deposit(wallet, amount);
                }
                case WITHDRAW -> {
                    wallet.withdraw(amount);
                    yield Operation.withdraw(wallet, amount);
                }
                case TRANSFER -> throw new IllegalArgumentException("Transfer is not a single-wallet operation");
            };
            operationRepository.save(operation);
            return wallet;
        });
    }

    public Wallet transfer(UUID userId, String toUsername, long amount) {
        try {
            Wallet from = doTransfer(userId, toUsername, amount);
            metrics.counter(TRANSFERS, "result", SUCCESS).increment();
            metrics.summary(AMOUNT, "type", tag(TRANSFER)).record(amount);
            return from;
        } catch (DomainException ex) {
            metrics.counter(TRANSFERS, "result", result(ex)).increment();
            throw ex;
        } catch (UserDirectoryUnavailableException ex) {
            metrics.counter(TRANSFERS, "result", "user_service_unavailable").increment();
            throw ex;
        }
    }

    private Wallet doTransfer(UUID userId, String toUsername, long amount) {
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

    private static String tag(OperationType type) {
        return type.name().toLowerCase(Locale.ROOT);
    }

    private static String result(DomainException ex) {
        return ex.getCode().toLowerCase(Locale.ROOT);
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
