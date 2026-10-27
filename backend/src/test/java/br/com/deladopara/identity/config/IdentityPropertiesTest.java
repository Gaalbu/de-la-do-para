package br.com.deladopara.identity.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class IdentityPropertiesTest {

    @Test
    void rejectsVerificationLinkThatCouldSendTheTokenOverHttp() {
        assertThatThrownBy(() -> new IdentityProperties(
                        12,
                        Duration.ofMinutes(30),
                        "http://localhost/verify-email",
                        Duration.ofMinutes(15),
                        "https://shop.example/reset-password",
                        "no-reply@example.test"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTPS");
    }

    @Test
    void rejectsVerificationUrlWithExistingQueryOrFragment() {
        assertThatThrownBy(() -> new IdentityProperties(
                        12,
                        Duration.ofMinutes(30),
                        "https://shop.example/verify?next=home",
                        Duration.ofMinutes(15),
                        "https://shop.example/reset-password",
                        "no-reply@example.test"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTPS");
        assertThatThrownBy(() -> new IdentityProperties(
                        12,
                        Duration.ofMinutes(30),
                        "https://shop.example/verify#existing",
                        Duration.ofMinutes(15),
                        "https://shop.example/reset-password",
                        "no-reply@example.test"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTPS");
    }
}
