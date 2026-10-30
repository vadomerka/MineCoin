package com.minecoin.finance.infrastructure.fault;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.fault")
public record FaultInjectionProperties(Duration delay, double errorRate) {

    public FaultInjectionProperties {
        if (delay == null || delay.isNegative()) {
            throw new IllegalArgumentException("FAULT_DELAY must be a non-negative duration");
        }
        if (errorRate < 0 || errorRate > 1) {
            throw new IllegalArgumentException("FAULT_ERROR_RATE must be between 0 and 1");
        }
    }

    public boolean active() {
        return !delay.isZero() || errorRate > 0;
    }
}
