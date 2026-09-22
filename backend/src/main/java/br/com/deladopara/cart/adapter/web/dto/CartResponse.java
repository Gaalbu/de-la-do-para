package br.com.deladopara.cart.adapter.web.dto;

import br.com.deladopara.cart.adapter.persistence.CartEntity;
import java.util.List;
import java.util.UUID;

public record CartResponse(UUID id, long version, List<Item> items) {
    public static CartResponse from(CartEntity cart) {
        return new CartResponse(
                cart.getId(),
                cart.getVersion(),
                cart.getItems().stream()
                        .map(item -> new Item(item.getSkuId(), item.getQuantity()))
                        .toList());
    }

    public record Item(UUID skuId, int quantity) {}
}
