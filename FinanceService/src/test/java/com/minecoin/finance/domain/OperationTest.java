package com.minecoin.finance.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minecoin.finance.domain.exception.SelfTransferException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class OperationTest {

    private final Wallet alice = walletWithId();
    private final Wallet bob = walletWithId();

    @Test
    void depositHasOnlyRecipientWallet() {
        Operation operation = Operation.deposit(alice, 100);

        assertThat(operation.getType()).isEqualTo(OperationType.DEPOSIT);
        assertThat(operation.getFromWalletId()).isNull();
        assertThat(operation.getToWalletId()).isEqualTo(alice.getId());
        assertThat(operation.getAmount()).isEqualTo(100);
    }

    @Test
    void withdrawHasOnlySenderWallet() {
        Operation operation = Operation.withdraw(alice, 100);

        assertThat(operation.getType()).isEqualTo(OperationType.WITHDRAW);
        assertThat(operation.getFromWalletId()).isEqualTo(alice.getId());
        assertThat(operation.getToWalletId()).isNull();
    }

    @Test
    void transferHasBothWallets() {
        Operation operation = Operation.transfer(alice, bob, 100);

        assertThat(operation.getType()).isEqualTo(OperationType.TRANSFER);
        assertThat(operation.getFromWalletId()).isEqualTo(alice.getId());
        assertThat(operation.getToWalletId()).isEqualTo(bob.getId());
    }

    @Test
    void transferToSameWalletThrows() {
        assertThatThrownBy(() -> Operation.transfer(alice, alice, 100)).isInstanceOf(SelfTransferException.class);
    }

    @Test
    void nonPositiveAmountIsRejected() {
        assertThatThrownBy(() -> Operation.deposit(alice, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    private static Wallet walletWithId() {
        Wallet wallet = new Wallet();
        ReflectionTestUtils.setField(wallet, "id", UUID.randomUUID());
        return wallet;
    }
}
