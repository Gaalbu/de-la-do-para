package br.com.deladopara.identity.application;

public interface IdentityMailPort {
    void sendVerification(String email, String verificationUrl);
}
