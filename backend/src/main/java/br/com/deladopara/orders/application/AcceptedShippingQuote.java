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
        List<Integer> packageSequences,
        List<PackageManifest> packages) {

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
        packages = List.copyOf(packages);
        if (packages.isEmpty()
                || !packages.stream().map(PackageManifest::sequence).toList().equals(packageSequences)) {
            throw new IllegalArgumentException("accepted shipping package manifest is invalid");
        }
    }

    public record PackageManifest(
            int sequence,
            String fingerprint,
            String boxCode,
            String category,
            boolean fragile,
            int lengthMm,
            int widthMm,
            int heightMm,
            int totalWeightGrams,
            List<ProductLine> lines) {
        public PackageManifest {
            if (sequence < 1
                    || fingerprint == null
                    || fingerprint.isBlank()
                    || boxCode == null
                    || boxCode.isBlank()
                    || category == null
                    || category.isBlank()
                    || lengthMm < 1
                    || widthMm < 1
                    || heightMm < 1
                    || totalWeightGrams < 1
                    || lines == null
                    || lines.isEmpty()) {
                throw new IllegalArgumentException("accepted shipping package is invalid");
            }
            lines = List.copyOf(lines);
        }
    }

    public record ProductLine(
            UUID skuId,
            String productName,
            String salesUnit,
            int quantity,
            long unitPriceCents,
            long declaredValueCents) {
        public ProductLine {
            if (skuId == null
                    || productName == null
                    || productName.isBlank()
                    || salesUnit == null
                    || salesUnit.isBlank()
                    || quantity < 1
                    || unitPriceCents < 0
                    || declaredValueCents != Math.multiplyExact(unitPriceCents, quantity)) {
                throw new IllegalArgumentException("accepted shipping product line is invalid");
            }
        }
    }
}
