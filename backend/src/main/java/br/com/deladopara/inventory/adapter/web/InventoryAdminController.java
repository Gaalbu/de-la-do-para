package br.com.deladopara.inventory.adapter.web;

import br.com.deladopara.inventory.adapter.persistence.InventoryAdjustmentService;
import br.com.deladopara.inventory.adapter.web.dto.InventoryAdjustmentRequest;
import br.com.deladopara.inventory.adapter.web.dto.InventoryLotCreateRequest;
import br.com.deladopara.inventory.adapter.web.dto.InventoryLotResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/inventory")
public class InventoryAdminController {

    private final InventoryAdjustmentService inventory;

    public InventoryAdminController(InventoryAdjustmentService inventory) {
        this.inventory = inventory;
    }

    @GetMapping("/skus/{skuId}/lots")
    public List<InventoryLotResponse> list(@PathVariable UUID skuId) {
        return inventory.list(skuId);
    }

    @PostMapping("/skus/{skuId}/lots")
    public InventoryLotResponse receive(
            @PathVariable UUID skuId, @Valid @RequestBody InventoryLotCreateRequest request) {
        return inventory.receive(skuId, request);
    }

    @PatchMapping("/lots/{lotId}")
    public InventoryLotResponse adjust(
            @PathVariable UUID lotId, @Valid @RequestBody InventoryAdjustmentRequest request) {
        return inventory.adjust(lotId, request);
    }
}
