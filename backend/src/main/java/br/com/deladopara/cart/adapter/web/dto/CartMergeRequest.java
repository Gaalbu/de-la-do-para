package br.com.deladopara.cart.adapter.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record CartMergeRequest(
        @NotNull Choice choice,
        @PositiveOrZero long expectedAccountVersion,
        @PositiveOrZero long expectedGuestVersion) {
    public enum Choice {
        KEEP_ACCOUNT,
        REPLACE_WITH_GUEST,
        COMBINE
    }
}
