package com.minecoin.prober;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProberTest {

    private static final Instant NOW = Instant.parse("2026-10-30T10:00:00Z");
    private static final ProberConfig CONFIG = new ProberConfig(
            URI.create("http://frontend"), "prober", "prober_sink", "secret123",
            Duration.ofSeconds(10), Duration.ofSeconds(5), 9090);

    FakeApi api;
    SimpleMeterRegistry metrics;
    Prober prober;

    @BeforeEach
    void setUp() {
        api = new FakeApi();
        metrics = new SimpleMeterRegistry();
        prober = new Prober(api, CONFIG, metrics, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void successfulRunChecksEveryStepAndKeepsBalance() {
        prober.run();

        assertThat(api.calls).containsExactly(
                "register prober", "register prober_sink", "home", "login prober",
                "wallet", "deposit 1", "transfer prober_sink 1");
        assertThat(count("minecoin.prober.runs", "result", "success")).isEqualTo(1);
        assertThat(count("minecoin.prober.checks", "step", "transfer", "result", "success")).isEqualTo(1);
    }

    @Test
    void accountsAreRegisteredOnlyOnce() {
        prober.run();
        prober.run();

        assertThat(api.calls).filteredOn(call -> call.startsWith("register")).hasSize(2);
    }

    @Test
    void walletStepsUseCachedTokenWhenLoginFails() {
        prober.run();
        api.failing.add("login");
        api.failing.add("transfer");

        prober.run();

        assertThat(count("minecoin.prober.runs", "result", "failure")).isEqualTo(1);
        assertThat(count("minecoin.prober.checks", "step", "login", "result", "failure")).isEqualTo(1);
        assertThat(count("minecoin.prober.checks", "step", "deposit", "result", "success")).isEqualTo(2);
        assertThat(count("minecoin.prober.checks", "step", "transfer", "result", "failure")).isEqualTo(1);
    }

    @Test
    void runFailsWithoutWalletChecksWhenThereIsNoToken() {
        api.failing.add("login");

        prober.run();

        assertThat(count("minecoin.prober.runs", "result", "failure")).isEqualTo(1);
        assertThat(api.calls).doesNotContain("wallet");
    }

    @Test
    void unauthorizedResponseDropsCachedToken() {
        prober.run();
        api.failing.add("login");
        api.unauthorized = true;

        prober.run();
        api.calls.clear();
        prober.run();

        assertThat(api.calls).doesNotContain("deposit 1");
    }

    private double count(String name, String... tags) {
        Counter counter = metrics.find(name).tags(tags).counter();
        return counter == null ? 0 : counter.count();
    }

    private static class FakeApi implements MineCoinApi {

        final List<String> calls = new ArrayList<>();
        final Set<String> failing = new HashSet<>();
        boolean unauthorized;

        @Override
        public void home() {
            call("home", "home");
        }

        @Override
        public void register(String username, String password) {
            call("register", "register " + username);
        }

        @Override
        public Session login(String username, String password) {
            call("login", "login " + username);
            return new Session("token", NOW.plus(Duration.ofHours(1)));
        }

        @Override
        public void wallet(String token) {
            if (unauthorized) {
                calls.add("wallet");
                throw new ProbeFailedException("GET /api/wallets/me returned 401", 401);
            }
            call("wallet", "wallet");
        }

        @Override
        public void deposit(String token, long amount) {
            call("deposit", "deposit " + amount);
        }

        @Override
        public void transfer(String token, String toUsername, long amount) {
            call("transfer", "transfer " + toUsername + " " + amount);
        }

        private void call(String step, String description) {
            calls.add(description);
            if (failing.contains(step)) {
                throw new ProbeFailedException(step + " returned 503", 503);
            }
        }
    }
}
