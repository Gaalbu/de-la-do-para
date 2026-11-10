package br.com.deladopara.identity.application;

import java.time.Duration;

public interface IdentityMailPort {
    void sendVerification(String email, String verificationUrl);

    void sendRecovery(String email, String recoveryUrl, Duration tokenTtl);
}
