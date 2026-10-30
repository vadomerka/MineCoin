package com.minecoin.prober;

import com.minecoin.prober.MineCoinApi.ProbeFailedException;
import com.minecoin.prober.MineCoinApi.Session;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.Clock;
import java.time.Duration;

public class Prober {

    private static final Logger log = System.getLogger(Prober.class.getName());
    private static final String RUNS = "minecoin.prober.runs";
    private static final String CHECKS = "minecoin.prober.checks";
    private static final String DURATION = "minecoin.prober.check.duration";
    private static final Duration TOKEN_RENEWAL_MARGIN = Duration.ofMinutes(1);
    private static final long AMOUNT = 1;

    private final MineCoinApi api;
    private final ProberConfig config;
    private final MeterRegistry metrics;
    private final Clock clock;
    private boolean accountsReady;
    private Session session;

    public Prober(MineCoinApi api, ProberConfig config, MeterRegistry metrics, Clock clock) {
        this.api = api;
        this.config = config;
        this.metrics = metrics;
        this.clock = clock;
    }

    public void run() {
        ensureAccounts();
        boolean succeeded = check("frontend", api::home);
        succeeded &= check("login", () -> session = api.login(config.username(), config.password()));
        if (hasValidSession()) {
            String token = session.token();
            succeeded &= check("wallet", () -> api.wallet(token));
            succeeded &= check("deposit", () -> api.deposit(token, AMOUNT));
            succeeded &= check("transfer", () -> api.transfer(token, config.recipient(), AMOUNT));
        } else {
            succeeded = false;
        }
        metrics.counter(RUNS, "result", succeeded ? "success" : "failure").increment();
    }

    private void ensureAccounts() {
        if (accountsReady) {
            return;
        }
        try {
            api.register(config.username(), config.password());
            api.register(config.recipient(), config.password());
            accountsReady = true;
        } catch (ProbeFailedException ex) {
            log.log(Level.WARNING, "Prober accounts are not ready: {0}", ex.getMessage());
        }
    }

    private boolean check(String step, Runnable action) {
        Timer.Sample sample = Timer.start(metrics);
        try {
            action.run();
            metrics.counter(CHECKS, "step", step, "result", "success").increment();
            return true;
        } catch (RuntimeException ex) {
            if (ex instanceof ProbeFailedException failed && failed.status() == 401) {
                session = null;
            }
            metrics.counter(CHECKS, "step", step, "result", "failure").increment();
            log.log(Level.WARNING, "Check {0} failed: {1}", step, ex.getMessage());
            return false;
        } finally {
            sample.stop(metrics.timer(DURATION, "step", step));
        }
    }

    private boolean hasValidSession() {
        return session != null && clock.instant().isBefore(session.expiresAt().minus(TOKEN_RENEWAL_MARGIN));
    }
}
