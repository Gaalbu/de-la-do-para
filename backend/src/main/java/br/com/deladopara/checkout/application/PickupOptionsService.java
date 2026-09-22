package br.com.deladopara.checkout.application;

import br.com.deladopara.catalog.adapter.persistence.ProductSkuRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PickupOptionsService {

    private final ProductSkuRepository skus;
    private final ObjectMapper objectMapper;

    public PickupOptionsService(ProductSkuRepository skus, ObjectMapper objectMapper) {
        this.skus = skus;
        this.objectMapper = objectMapper;
    }

    public PickupOptions evaluate(String snapshotItems) {
        try {
            var items =
                    objectMapper.readValue(snapshotItems, new TypeReference<List<CheckoutSnapshotService.Item>>() {});
            var unavailable = items.stream()
                    .map(CheckoutSnapshotService.Item::skuId)
                    .filter(id -> !isPickupEligible(id))
                    .toList();
            if (!unavailable.isEmpty()) {
                return new PickupOptions("UNAVAILABLE", List.of(), unavailable);
            }
            return new PickupOptions(
                    "AVAILABLE",
                    List.of(new PickupOption(
                            "PONTO-DEMO-BELEM",
                            "Ponto de demonstração — Belém",
                            "segunda a sexta, das 9h às 18h (horário de Belém)",
                            1)),
                    List.of());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("checkout snapshot items are invalid", exception);
        }
    }

    public PickupOption select(String snapshotItems, String optionId) {
        var options = evaluate(snapshotItems);
        if (!"AVAILABLE".equals(options.status())) {
            throw new PickupSelectionConflictException();
        }
        return options.options().stream()
                .filter(option -> option.id().equals(optionId))
                .findFirst()
                .orElseThrow(PickupSelectionConflictException::new);
    }

    private boolean isPickupEligible(UUID skuId) {
        return skus.findById(skuId)
                .filter(sku -> sku.isActive() && sku.isPickupEligible())
                .isPresent();
    }

    public record PickupOptions(String status, List<PickupOption> options, List<UUID> unavailableSkuIds) {}

    public record PickupOption(String id, String point, String window, int preparationDays) {}

    public static class PickupSelectionConflictException extends RuntimeException {}
}
