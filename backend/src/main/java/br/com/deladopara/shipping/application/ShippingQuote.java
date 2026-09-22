package br.com.deladopara.shipping.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ShippingQuote(
        UUID id,
        UUID snapshotId,
        long snapshotVersion,
        String destinationPostalCode,
        String inputFingerprint,
        String serviceId,
        String serviceName,
        long priceCents,
        int deliveryDays,
        int preparationDays,
        List<Integer> packageSequences,
        Instant createdAt,
        Instant expiresAt) {

    public ShippingQuote {
        if (id == null || snapshotId == null || snapshotVersion < 0) {
            throw new IllegalArgumentException("shipping quote snapshot identity is invalid");
        }
        if (inputFingerprint == null || inputFingerprint.isBlank()) {
            throw new IllegalArgumentException("shipping quote fingerprint is required");
        }
        if (priceCents < 0 || deliveryDays < 1 || preparationDays < 0) {
            throw new IllegalArgumentException("shipping quote values are invalid");
        }
        if (createdAt == null || expiresAt == null || !expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("shipping quote validity is invalid");
        }
        packageSequences = List.copyOf(packageSequences);
    }
}
