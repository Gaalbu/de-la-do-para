package br.com.deladopara.cart.adapter.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record CartItemRequest(@NotNull UUID skuId, @Positive int quantity) {}
