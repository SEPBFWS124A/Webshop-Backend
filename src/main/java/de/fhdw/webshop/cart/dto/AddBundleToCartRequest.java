package de.fhdw.webshop.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AddBundleToCartRequest(
        @NotNull Long bundleId,
        @NotNull @Min(1) Integer quantity
) {}
