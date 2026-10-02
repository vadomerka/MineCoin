package com.minecoin.finance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

import com.minecoin.finance.domain.OperationType;
import com.minecoin.finance.domain.exception.AmountLimitExceededException;
import com.minecoin.finance.domain.exception.InsufficientFundsException;
import com.minecoin.finance.domain.exception.RecipientNotFoundException;
import com.minecoin.finance.domain.exception.SelfTransferException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "app.finance.max-operation-amount=1000"
})
class WalletServiceIT {

    private static final PageRequest FIRST_PAGE = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    WalletService walletService;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    UserDirectory userDirectory;

    UUID alice;
    UUID bob;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE operations, wallets");
        alice = UUID.randomUUID();
        bob = UUID.randomUUID();
        when(userDirectory.findByUsername("bob")).thenReturn(Optional.of(new UserRef(bob, "bob")));
        when(userDirectory.findByUsername("alice")).thenReturn(Optional.of(new UserRef(alice, "alice")));
    }

    @Test
    void openWalletIsIdempotent() {
        UUID first = walletService.openWallet(alice).getId();
        UUID second = walletService.openWallet(alice).getId();

        assertThat(second).isEqualTo(first);
        assertThat(count("wallets")).isEqualTo(1);
    }

    @Test
    void depositAndWithdrawChangeBalanceAndRecordOperations() {
        walletService.deposit(alice, 500);

        assertThat(walletService.withdraw(alice, 200).getBalance()).isEqualTo(300);
        assertThat(balanceOf(alice)).isEqualTo(300);
        assertThat(count("operations")).isEqualTo(2);
    }

    @Test
    void failedWithdrawChangesNothing() {
        walletService.deposit(alice, 100);

        assertThatThrownBy(() -> walletService.withdraw(alice, 101)).isInstanceOf(InsufficientFundsException.class);

        assertThat(balanceOf(alice)).isEqualTo(100);
        assertThat(count("operations")).isEqualTo(1);
    }

    @Test
    void amountAboveLimitIsRejected() {
        assertThatThrownBy(() -> walletService.deposit(alice, 1001)).isInstanceOf(AmountLimitExceededException.class);
        assertThat(walletService.deposit(alice, 1000).getBalance()).isEqualTo(1000);
    }

    @Test
    void transferMovesMoneyAndOpensRecipientWallet() {
        walletService.deposit(alice, 500);

        assertThat(walletService.transfer(alice, "bob", 200).getBalance()).isEqualTo(300);

        assertThat(balanceOf(bob)).isEqualTo(200);
        assertThat(count("operations")).isEqualTo(2);
    }

    @Test
    void transferRejectsUnknownRecipientSelfTransferAndInsufficientFunds() {
        walletService.deposit(alice, 100);
        when(userDirectory.findByUsername("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.transfer(alice, "nobody", 10))
                .isInstanceOf(RecipientNotFoundException.class);
        assertThatThrownBy(() -> walletService.transfer(alice, "alice", 10))
                .isInstanceOf(SelfTransferException.class);
        assertThatThrownBy(() -> walletService.transfer(alice, "bob", 101))
                .isInstanceOf(InsufficientFundsException.class);
        assertThat(balanceOf(alice)).isEqualTo(100);
        assertThat(count("operations")).isEqualTo(1);
    }

    @Test
    void historyShowsDirectionAndCounterpartyUsername() {
        walletService.deposit(alice, 500);
        walletService.transfer(alice, "bob", 200);
        walletService.withdraw(alice, 50);
        when(userDirectory.findUsernames(anyCollection())).thenReturn(Map.of(bob, "bob"));

        List<OperationView> history = walletService.history(alice, FIRST_PAGE).getContent();

        assertThat(history).extracting(OperationView::type)
                .containsExactly(OperationType.WITHDRAW, OperationType.TRANSFER, OperationType.DEPOSIT);
        assertThat(history).extracting(OperationView::direction)
                .containsExactly(OperationDirection.OUT, OperationDirection.OUT, OperationDirection.IN);
        OperationView transfer = history.get(1);
        assertThat(transfer.counterpartyUserId()).isEqualTo(bob);
        assertThat(transfer.counterpartyUsername()).isEqualTo("bob");

        when(userDirectory.findUsernames(anyCollection())).thenReturn(Map.of(alice, "alice"));
        OperationView incoming = walletService.history(bob, FIRST_PAGE).getContent().getFirst();
        assertThat(incoming.direction()).isEqualTo(OperationDirection.IN);
        assertThat(incoming.counterpartyUsername()).isEqualTo("alice");
    }

    @Test
    void historyWorksWithoutUsernamesWhenUserDirectoryIsUnavailable() {
        walletService.deposit(alice, 500);
        walletService.transfer(alice, "bob", 200);
        when(userDirectory.findUsernames(anyCollection()))
                .thenThrow(new UserDirectoryUnavailableException(new RuntimeException("down")));

        Page<OperationView> history = walletService.history(alice, FIRST_PAGE);

        assertThat(history.getTotalElements()).isEqualTo(2);
        OperationView transfer = history.getContent().getFirst();
        assertThat(transfer.counterpartyUserId()).isEqualTo(bob);
        assertThat(transfer.counterpartyUsername()).isNull();
    }

    @Test
    void concurrentWithdrawalsNeverOverdraw() throws Exception {
        walletService.deposit(alice, 100);
        AtomicInteger rejected = new AtomicInteger();

        int succeeded = runConcurrently(20, () -> {
            try {
                walletService.withdraw(alice, 10);
                return true;
            } catch (InsufficientFundsException ex) {
                rejected.incrementAndGet();
                return false;
            }
        });

        assertThat(succeeded).isEqualTo(10);
        assertThat(rejected).hasValue(10);
        assertThat(balanceOf(alice)).isZero();
        assertThat(count("operations")).isEqualTo(11);
    }

    @Test
    void oppositeTransfersDoNotDeadlock() throws Exception {
        walletService.deposit(alice, 500);
        walletService.deposit(bob, 500);
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            tasks.add(() -> walletService.transfer(alice, "bob", 1) != null);
            tasks.add(() -> walletService.transfer(bob, "alice", 1) != null);
        }

        int succeeded = runConcurrently(tasks);

        assertThat(succeeded).isEqualTo(50);
        assertThat(balanceOf(alice)).isEqualTo(500);
        assertThat(balanceOf(bob)).isEqualTo(500);
    }

    private int runConcurrently(int times, Callable<Boolean> task) throws Exception {
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < times; i++) {
            tasks.add(task);
        }
        return runConcurrently(tasks);
    }

    private int runConcurrently(List<Callable<Boolean>> tasks) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(tasks.size())) {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (Callable<Boolean> task : tasks) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            int succeeded = 0;
            for (Future<Boolean> future : futures) {
                if (future.get(30, TimeUnit.SECONDS)) {
                    succeeded++;
                }
            }
            return succeeded;
        }
    }

    private long balanceOf(UUID userId) {
        return jdbc.queryForObject("SELECT balance FROM wallets WHERE user_id = ?", Long.class, userId);
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }
}
