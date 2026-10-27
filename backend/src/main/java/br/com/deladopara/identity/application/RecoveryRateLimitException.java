package br.com.deladopara.identity.application;

public class RecoveryRateLimitException extends RuntimeException {
    private final int retryAfterSeconds;

    public RecoveryRateLimitException(int retryAfterSeconds) {
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
