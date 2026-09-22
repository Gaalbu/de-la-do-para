package br.com.deladopara.shipping.adapter;

import java.time.Duration;

public record ShippingAdapterProperties(String baseUrl, String credential, Duration timeout) {

    public ShippingAdapterProperties {
        if (baseUrl == null || baseUrl.isBlank() || credential == null || credential.isBlank()) {
            throw new IllegalArgumentException("shipping adapter configuration is incomplete");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("shipping adapter timeout is invalid");
        }
    }
}
