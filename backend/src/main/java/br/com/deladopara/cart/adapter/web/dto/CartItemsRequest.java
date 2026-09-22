package br.com.deladopara.cart.adapter.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;

public record CartItemsRequest(@PositiveOrZero long expectedVersion, List<@Valid CartItemRequest> items) {}
