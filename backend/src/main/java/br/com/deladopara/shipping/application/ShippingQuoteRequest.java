package br.com.deladopara.shipping.application;

import br.com.deladopara.shipping.domain.PackageComposer.PackagePlan;
import java.util.List;

public record ShippingQuoteRequest(String destinationPostalCode, List<PackagePlan> packages) {

    public ShippingQuoteRequest {
        if (destinationPostalCode == null || !destinationPostalCode.matches("\\d{5}-?\\d{3}")) {
            throw new IllegalArgumentException("destination postal code is invalid");
        }
        if (packages == null || packages.isEmpty()) {
            throw new IllegalArgumentException("shipping packages are required");
        }
        packages = List.copyOf(packages);
    }
}
