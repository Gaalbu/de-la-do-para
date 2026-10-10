package br.com.deladopara.shipping.application;

import br.com.deladopara.shipping.domain.PackageComposer;
import br.com.deladopara.shipping.domain.PackageComposer.PackagePlan;
import java.time.Instant;
import java.util.HashSet;
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
        List<PackageManifest> packages,
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
        packages = List.copyOf(packages);
        if (packages.isEmpty()
                || !packages.stream().map(PackageManifest::sequence).toList().equals(packageSequences)
                || new HashSet<>(packageSequences).size() != packageSequences.size()) {
            throw new IllegalArgumentException("shipping package manifest does not match quoted sequences");
        }
    }

    public record PackageManifest(
            int sequence,
            String boxCode,
            PackageComposer.Category category,
            boolean fragile,
            int lengthMm,
            int widthMm,
            int heightMm,
            int totalWeightGrams,
            List<PackageLine> lines) {
        public PackageManifest {
            if (sequence < 1
                    || boxCode == null
                    || boxCode.isBlank()
                    || category == null
                    || lengthMm < 1
                    || widthMm < 1
                    || heightMm < 1
                    || totalWeightGrams < 1
                    || lines == null
                    || lines.isEmpty()) {
                throw new IllegalArgumentException("shipping package manifest is invalid");
            }
            lines = List.copyOf(lines);
        }

        public static PackageManifest from(PackagePlan plan) {
            return new PackageManifest(
                    plan.sequence(),
                    plan.boxCode(),
                    plan.category(),
                    plan.fragile(),
                    plan.lengthMm(),
                    plan.widthMm(),
                    plan.heightMm(),
                    plan.totalWeightGrams(),
                    plan.lines().stream()
                            .map(line -> new PackageLine(line.skuId(), line.quantity()))
                            .toList());
        }
    }

    public record PackageLine(UUID skuId, int quantity) {
        public PackageLine {
            if (skuId == null || quantity < 1) {
                throw new IllegalArgumentException("shipping package line is invalid");
            }
        }
    }
}
