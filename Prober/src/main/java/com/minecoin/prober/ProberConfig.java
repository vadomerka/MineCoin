package com.minecoin.prober;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

public record ProberConfig(
        URI baseUrl,
        String username,
        String recipient,
        String password,
        Duration interval,
        Duration timeout,
        int metricsPort) {

    public static ProberConfig fromEnvironment(Map<String, String> env) {
        return new ProberConfig(
                URI.create(env.getOrDefault("PROBER_BASE_URL", "http://localhost:8080")),
                env.getOrDefault("PROBER_USERNAME", "prober"),
                env.getOrDefault("PROBER_RECIPIENT", "prober_sink"),
                required(env, "PROBER_PASSWORD"),
                Duration.ofSeconds(Long.parseLong(env.getOrDefault("PROBER_INTERVAL_SECONDS", "10"))),
                Duration.ofSeconds(Long.parseLong(env.getOrDefault("PROBER_TIMEOUT_SECONDS", "5"))),
                Integer.parseInt(env.getOrDefault("PROBER_METRICS_PORT", "9090")));
    }

    private static String required(Map<String, String> env, String name) {
        String value = env.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set");
        }
        return value;
    }
}
