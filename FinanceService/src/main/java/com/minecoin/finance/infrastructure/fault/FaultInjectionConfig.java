package com.minecoin.finance.infrastructure.fault;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Slf4j
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class FaultInjectionConfig implements WebMvcConfigurer {

    private final FaultInjectionProperties properties;

    public FaultInjectionConfig(FaultInjectionProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (!properties.active()) {
            return;
        }
        log.warn("Fault injection enabled: delay {}, error rate {}", properties.delay(), properties.errorRate());
        registry.addInterceptor(new FaultInjectionInterceptor(properties)).addPathPatterns("/api/**");
    }
}
