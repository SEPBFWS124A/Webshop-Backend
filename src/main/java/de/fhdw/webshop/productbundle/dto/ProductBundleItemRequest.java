package de.fhdw.webshop.productbundle.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ProductBundleItemRequest(
        @NotNull Long productId,
        @NotNull @Min(1) Integer quantity
) {}
