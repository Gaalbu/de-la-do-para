package br.com.deladopara.inventory.domain;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record ReservationIntent(String reference, LocalDate arrivalDate, List<Line> lines) {

    public ReservationIntent {
        if (reference == null || reference.isBlank() || arrivalDate == null || lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("Incomplete reservation intent");
        }
        Set<String> skus = new HashSet<>();
        for (Line line : lines) {
            if (line == null
                    || line.skuCode() == null
                    || line.skuCode().isBlank()
                    || line.quantity() <= 0
                    || !skus.add(line.skuCode())) {
                throw new IllegalArgumentException("Reservation lines must be positive and unique");
            }
        }
    }

    public record Line(String skuCode, int quantity) {}
}
