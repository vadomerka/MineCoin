package com.minecoin.finance.infrastructure.web;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wallets")
@RequiredArgsConstructor
public class VersionController {

    private final ReleaseInfo releaseInfo;

    @GetMapping("/version")
    public ReleaseInfo version() {
        return releaseInfo;
    }
}
