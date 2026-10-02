package com.minecoin.finance.domain;

import com.minecoin.finance.domain.exception.SelfTransferException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "operations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Operation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OperationType type;

    private UUID fromWalletId;

    private UUID toWalletId;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private Instant createdAt;

    public static Operation deposit(Wallet to, long amount) {
        return create(OperationType.DEPOSIT, null, to.getId(), amount);
    }

    public static Operation withdraw(Wallet from, long amount) {
        return create(OperationType.WITHDRAW, from.getId(), null, amount);
    }

    public static Operation transfer(Wallet from, Wallet to, long amount) {
        if (from.getId().equals(to.getId())) {
            throw new SelfTransferException();
        }
        return create(OperationType.TRANSFER, from.getId(), to.getId(), amount);
    }

    private static Operation create(OperationType type, UUID fromWalletId, UUID toWalletId, long amount) {
        Wallet.requirePositive(amount);
        Operation operation = new Operation();
        operation.type = type;
        operation.fromWalletId = fromWalletId;
        operation.toWalletId = toWalletId;
        operation.amount = amount;
        return operation;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
