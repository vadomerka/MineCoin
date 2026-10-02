package com.minecoin.finance.infrastructure.web;

import com.minecoin.finance.infrastructure.web.dto.AmountRequest;
import com.minecoin.finance.infrastructure.web.dto.LimitsResponse;
import com.minecoin.finance.infrastructure.web.dto.OperationResponse;
import com.minecoin.finance.infrastructure.web.dto.PageResponse;
import com.minecoin.finance.infrastructure.web.dto.TransferRequest;
import com.minecoin.finance.infrastructure.web.dto.WalletResponse;
import com.minecoin.finance.service.WalletService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wallets")
@RequiredArgsConstructor
public class WalletController {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt", "id");

    private final WalletService walletService;

    @GetMapping("/me")
    public WalletResponse getMyWallet(@AuthenticationPrincipal Jwt jwt) {
        return WalletResponse.from(walletService.openWallet(currentUserId(jwt)));
    }

    @PostMapping("/me/deposit")
    public WalletResponse deposit(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AmountRequest request) {
        return WalletResponse.from(walletService.deposit(currentUserId(jwt), request.amount()));
    }

    @PostMapping("/me/withdraw")
    public WalletResponse withdraw(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AmountRequest request) {
        return WalletResponse.from(walletService.withdraw(currentUserId(jwt), request.amount()));
    }

    @PostMapping("/me/transfer")
    public WalletResponse transfer(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TransferRequest request) {
        return WalletResponse.from(
                walletService.transfer(currentUserId(jwt), request.toUsername(), request.amount()));
    }

    @GetMapping("/me/operations")
    public PageResponse<OperationResponse> operations(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageRequest pageable = PageRequest.of(page, size, NEWEST_FIRST);
        return PageResponse.from(walletService.history(currentUserId(jwt), pageable), OperationResponse::from);
    }

    @GetMapping("/limits")
    public LimitsResponse limits() {
        return new LimitsResponse(walletService.maxOperationAmount());
    }

    private UUID currentUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
