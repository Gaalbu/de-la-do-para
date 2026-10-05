package br.com.deladopara.identity.config;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.identity")
public record IdentityProperties(
        int bcryptStrength, Duration verificationTokenTtl, String verificationUrl, String mailFrom) {

    public IdentityProperties {
        Objects.requireNonNull(verificationTokenTtl, "app.identity.verification-token-ttl is required");
        if (verificationTokenTtl.isZero() || verificationTokenTtl.isNegative()) {
            throw new IllegalArgumentException("app.identity.verification-token-ttl must be positive");
        }
        var uri = URI.create(Objects.requireNonNull(verificationUrl, "app.identity.verification-url is required"));
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null
                || uri.getUserInfo() != null) {
            throw new IllegalArgumentException(
                    "app.identity.verification-url must be an HTTPS URL without query or fragment");
        }
        Objects.requireNonNull(mailFrom, "app.identity.mail-from is required");
    }
}
