package com.minecoin.finance.service;

import com.minecoin.finance.domain.Operation;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OperationRepository extends JpaRepository<Operation, UUID> {

    @Query("SELECT o FROM Operation o WHERE o.fromWalletId = :walletId OR o.toWalletId = :walletId")
    Page<Operation> findAllByWalletId(UUID walletId, Pageable pageable);
}
