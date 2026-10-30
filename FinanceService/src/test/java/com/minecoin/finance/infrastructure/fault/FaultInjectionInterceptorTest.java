package com.minecoin.finance.infrastructure.fault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class FaultInjectionInterceptorTest {

    @Test
    void inactivePropertiesPassRequestThrough() throws Exception {
        FaultInjectionProperties properties = new FaultInjectionProperties(Duration.ZERO, 0);

        assertThat(properties.active()).isFalse();
        assertThat(preHandle(properties)).isTrue();
    }

    @Test
    void fullErrorRateFailsRequest() {
        assertThatThrownBy(() -> preHandle(new FaultInjectionProperties(Duration.ZERO, 1)))
                .isInstanceOf(InjectedFaultException.class);
    }

    @Test
    void delayIsAppliedBeforeHandling() throws Exception {
        long started = System.nanoTime();

        boolean proceed = preHandle(new FaultInjectionProperties(Duration.ofMillis(200), 0));

        assertThat(proceed).isTrue();
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isGreaterThanOrEqualTo(Duration.ofMillis(200));
    }

    @Test
    void errorRateOutsideRangeIsRejected() {
        assertThatThrownBy(() -> new FaultInjectionProperties(Duration.ZERO, 1.5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativeDelayIsRejected() {
        assertThatThrownBy(() -> new FaultInjectionProperties(Duration.ofMillis(-1), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private boolean preHandle(FaultInjectionProperties properties) throws Exception {
        return new FaultInjectionInterceptor(properties)
                .preHandle(new MockHttpServletRequest("GET", "/api/wallets/me"), new MockHttpServletResponse(), new Object());
    }
}
