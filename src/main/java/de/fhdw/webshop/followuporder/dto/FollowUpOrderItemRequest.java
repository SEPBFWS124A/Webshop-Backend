package de.fhdw.webshop.followuporder.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record FollowUpOrderItemRequest(
    @NotNull Long productId,
    @Min(1) int quantity
) {}
