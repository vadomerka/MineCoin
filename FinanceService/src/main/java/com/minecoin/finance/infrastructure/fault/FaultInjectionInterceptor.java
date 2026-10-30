package com.minecoin.finance.infrastructure.fault;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.web.servlet.HandlerInterceptor;

public class FaultInjectionInterceptor implements HandlerInterceptor {

    private final FaultInjectionProperties properties;

    public FaultInjectionInterceptor(FaultInjectionProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws InterruptedException {
        Thread.sleep(properties.delay());
        if (ThreadLocalRandom.current().nextDouble() < properties.errorRate()) {
            throw new InjectedFaultException();
        }
        return true;
    }
}
