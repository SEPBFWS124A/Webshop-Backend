package de.fhdw.webshop.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PlaceOrderItemRequest(
        @NotNull Long productId,
        @Min(1) int quantity,
        String personalizationText
) {}
