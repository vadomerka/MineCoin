package com.minecoin.finance.infrastructure.client;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.user-service")
public record UserServiceProperties(URI url, Duration connectTimeout, Duration readTimeout) {

    public UserServiceProperties {
        if (url == null || !"http".equals(url.getScheme()) && !"https".equals(url.getScheme())) {
            throw new IllegalArgumentException("USER_SERVICE_URL must be an http(s) URL");
        }
    }
}
