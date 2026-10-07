package br.com.deladopara.orders.application;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** Immutable quote facts copied into an accepted delivery order. */
public record AcceptedShippingQuote(
        UUID snapshotId,
        long snapshotVersion,
        UUID quoteId,
        String inputFingerprint,
        String serviceId,
        String serviceName,
        long priceCents,
        int preparationDays,
        int deliveryDays,
        List<Integer> packageSequences) {

    public AcceptedShippingQuote {
        if (snapshotId == null || snapshotVersion < 0 || quoteId == null) {
            throw new IllegalArgumentException("accepted shipping quote identity is invalid");
        }
        if (inputFingerprint == null
                || inputFingerprint.isBlank()
                || serviceId == null
                || serviceId.isBlank()
                || serviceName == null
                || serviceName.isBlank()) {
            throw new IllegalArgumentException("accepted shipping quote details are required");
        }
        if (priceCents < 0 || preparationDays < 0 || deliveryDays < 1) {
            throw new IllegalArgumentException("accepted shipping quote values are invalid");
        }
        if (packageSequences == null
                || packageSequences.isEmpty()
                || packageSequences.stream().anyMatch(sequence -> sequence == null || sequence < 1)
                || new HashSet<>(packageSequences).size() != packageSequences.size()) {
            throw new IllegalArgumentException("accepted shipping package sequences are invalid");
        }
        packageSequences = List.copyOf(packageSequences);
    }
}
