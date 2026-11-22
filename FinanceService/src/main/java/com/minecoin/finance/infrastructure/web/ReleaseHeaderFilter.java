package com.minecoin.finance.infrastructure.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ReleaseHeaderFilter extends OncePerRequestFilter {

    public static final String VERSION_HEADER = "X-App-Version";
    public static final String POD_HEADER = "X-Pod";

    private final ReleaseInfo releaseInfo;

    public ReleaseHeaderFilter(ReleaseInfo releaseInfo) {
        this.releaseInfo = releaseInfo;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader(VERSION_HEADER, releaseInfo.version());
        response.setHeader(POD_HEADER, releaseInfo.pod());
        chain.doFilter(request, response);
    }
}
