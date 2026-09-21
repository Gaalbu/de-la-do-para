package br.com.deladopara.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.identity")
public record IdentityProperties(
        int bcryptStrength,
        String verificationTokenTtl,
        String recoveryTokenTtl,
        Session session,
        String mailFrom,
        String mailBaseUrl) {

    public record Session(String idleTimeout, String absoluteTimeout) {}
}
