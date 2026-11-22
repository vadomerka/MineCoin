package com.minecoin.finance.infrastructure.web;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReleaseConfig {

    @Bean
    public ReleaseInfo releaseInfo(
            @Value("${spring.application.name}") String service,
            @Value("${app.version}") String version) {
        return new ReleaseInfo(service, version, hostname());
    }

    private static String hostname() {
        String fromEnvironment = System.getenv("HOSTNAME");
        if (fromEnvironment != null && !fromEnvironment.isBlank()) {
            return fromEnvironment;
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException ex) {
            return "unknown";
        }
    }
}
