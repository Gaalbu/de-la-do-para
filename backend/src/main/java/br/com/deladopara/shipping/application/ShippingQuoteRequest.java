package br.com.deladopara.shipping.application;

import br.com.deladopara.shipping.domain.PackageComposer.PackagePlan;
import java.util.List;
import java.util.Map;

public record ShippingQuoteRequest(
        String originPostalCode,
        String destinationPostalCode,
        List<PackagePlan> packages,
        Map<Integer, Long> insuranceValueCentsByPackage) {

    public ShippingQuoteRequest {
        if (destinationPostalCode == null || !destinationPostalCode.matches("\\d{5}-?\\d{3}")) {
            throw new IllegalArgumentException("destination postal code is invalid");
        }
        if (packages == null || packages.isEmpty()) {
            throw new IllegalArgumentException("shipping packages are required");
        }
        if (originPostalCode == null || !originPostalCode.matches("\\d{8}")) {
            throw new IllegalArgumentException("origin postal code is invalid");
        }
        var packageCopy = List.copyOf(packages);
        var insuranceCopy = Map.copyOf(insuranceValueCentsByPackage);
        if (insuranceCopy.size() != packageCopy.size()) {
            throw new IllegalArgumentException("shipping package insurance values are incomplete");
        }
        for (var packagePlan : packageCopy) {
            var insuranceCents = insuranceCopy.get(packagePlan.sequence());
            if (insuranceCents == null || insuranceCents < 0) {
                throw new IllegalArgumentException("shipping package insurance values are incomplete");
            }
        }
        packages = packageCopy;
        insuranceValueCentsByPackage = insuranceCopy;
    }
}
