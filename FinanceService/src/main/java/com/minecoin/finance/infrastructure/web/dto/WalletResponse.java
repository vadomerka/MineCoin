package com.minecoin.finance.infrastructure.web.dto;

import com.minecoin.finance.domain.Wallet;
import java.util.UUID;

public record WalletResponse(UUID id, long balance) {

    public static WalletResponse from(Wallet wallet) {
        return new WalletResponse(wallet.getId(), wallet.getBalance());
    }
}
