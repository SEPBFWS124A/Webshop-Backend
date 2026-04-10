package de.fhdw.webshop.standingorder.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record StandingOrderItemRequest(
        @NotNull Long productId,
        @Min(1) int quantity
) {}
