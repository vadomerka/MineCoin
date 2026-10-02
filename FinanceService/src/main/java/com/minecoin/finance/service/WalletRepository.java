package com.minecoin.finance.service;

import com.minecoin.finance.domain.Wallet;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    Optional<Wallet> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    @Modifying
    @Query(value = "INSERT INTO wallets (user_id) VALUES (:userId) ON CONFLICT (user_id) DO NOTHING",
            nativeQuery = true)
    void insertIfAbsent(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.userId IN :userIds ORDER BY w.id")
    List<Wallet> lockAllByUserIdsOrderedById(Collection<UUID> userIds);
}
