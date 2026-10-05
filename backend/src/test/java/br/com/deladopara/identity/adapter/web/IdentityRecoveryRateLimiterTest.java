package br.com.deladopara.identity.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class IdentityRecoveryRateLimiterTest {

    @Test
    void limitsThreeRequestsPerIpAndNormalizedEmailForOneHour() {
        var now = new AtomicReference<>(Instant.parse("2026-09-25T12:00:00Z"));
        var clock = new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneId.of("UTC");
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        };
        var limiter = new IdentityRecoveryRateLimiter(clock);

        limiter.acquire("192.0.2.1", "Customer@example.com");
        limiter.acquire("192.0.2.1", "customer@example.com");
        limiter.acquire("192.0.2.1", "customer@example.com");
        assertThatThrownBy(() -> limiter.acquire("192.0.2.1", "customer@example.com"))
                .isInstanceOf(IdentityRecoveryRateLimiter.RateLimitExceededException.class)
                .satisfies(error -> assertThat(
                                ((IdentityRecoveryRateLimiter.RateLimitExceededException) error).getRetryAfterSeconds())
                        .isEqualTo(3600));
        limiter.acquire("192.0.2.2", "customer@example.com");

        now.set(now.get().plus(Duration.ofHours(1)));
        limiter.acquire("192.0.2.1", "customer@example.com");
    }
}
