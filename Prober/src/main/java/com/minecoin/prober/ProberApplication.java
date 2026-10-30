package com.minecoin.prober;

import com.sun.net.httpserver.HttpServer;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ProberApplication {

    private static final Logger log = System.getLogger(ProberApplication.class.getName());

    public static void main(String[] args) throws IOException {
        ProberConfig config = ProberConfig.fromEnvironment(System.getenv());
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        registry.config().commonTags("application", "prober");
        Prober prober = new Prober(
                new HttpMineCoinApi(config.baseUrl(), config.timeout()), config, registry, Clock.systemUTC());

        HttpServer server = HttpServer.create(new InetSocketAddress(config.metricsPort()), 0);
        server.createContext("/metrics", exchange -> {
            byte[] body = registry.scrape().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; version=0.0.4; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                prober.run();
            } catch (RuntimeException ex) {
                log.log(Level.ERROR, "Probe run failed", ex);
            }
        }, 0, config.interval().toMillis(), TimeUnit.MILLISECONDS);
        log.log(Level.INFO, "Prober started: target {0}, every {1}, metrics on :{2,number,#}",
                config.baseUrl(), config.interval(), config.metricsPort());
    }
}
