package br.com.deladopara.orders.application;

import br.com.deladopara.orders.domain.FulfillmentMode;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;

/** Values captured at purchase time. Orders never reference live catalog or pricing rows. */
public record CreateOrderCommand(
        String checkoutKey,
        UUID accountId,
        String contactEmail,
        FulfillmentMode mode,
        long subtotalCents,
        long shippingCents,
        String discountType,
        Long discountValue,
        long discountCents,
        String couponCode,
        long totalCents,
        int preparationDays,
        Integer deliveryDays,
        String pricingRuleVersion,
        JsonNode destination,
        List<Item> items,
        UUID correlationId) {

    public CreateOrderCommand {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("An order needs at least one item");
        }
        items = List.copyOf(items);
    }

    public record Item(
            UUID skuId, String productName, String skuLabel, int quantity, long unitPriceCents, long lineTotalCents) {}
}
