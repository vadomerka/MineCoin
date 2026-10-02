package com.minecoin.finance.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minecoin.finance.domain.exception.InsufficientFundsException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WalletTest {

    @Test
    void openCreatesEmptyWalletForUser() {
        UUID userId = UUID.randomUUID();

        Wallet wallet = Wallet.open(userId);

        assertThat(wallet.getUserId()).isEqualTo(userId);
        assertThat(wallet.getBalance()).isZero();
    }

    @Test
    void depositIncreasesBalance() {
        Wallet wallet = Wallet.open(UUID.randomUUID());

        wallet.deposit(300);
        wallet.deposit(200);

        assertThat(wallet.getBalance()).isEqualTo(500);
    }

    @Test
    void withdrawDecreasesBalanceDownToZero() {
        Wallet wallet = Wallet.open(UUID.randomUUID());
        wallet.deposit(500);

        wallet.withdraw(500);

        assertThat(wallet.getBalance()).isZero();
    }

    @Test
    void withdrawMoreThanBalanceThrowsAndKeepsBalance() {
        Wallet wallet = Wallet.open(UUID.randomUUID());
        wallet.deposit(100);

        assertThatThrownBy(() -> wallet.withdraw(101)).isInstanceOf(InsufficientFundsException.class);
        assertThat(wallet.getBalance()).isEqualTo(100);
    }

    @Test
    void nonPositiveAmountIsRejected() {
        Wallet wallet = Wallet.open(UUID.randomUUID());

        assertThatThrownBy(() -> wallet.deposit(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> wallet.withdraw(-5)).isInstanceOf(IllegalArgumentException.class);
    }
}
