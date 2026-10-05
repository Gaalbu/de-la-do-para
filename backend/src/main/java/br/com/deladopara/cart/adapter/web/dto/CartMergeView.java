package br.com.deladopara.cart.adapter.web.dto;

import java.util.List;
import java.util.UUID;

public record CartMergeView(CartResponse accountCart, CartResponse guestCart, List<UUID> inactiveSkuIds) {}
