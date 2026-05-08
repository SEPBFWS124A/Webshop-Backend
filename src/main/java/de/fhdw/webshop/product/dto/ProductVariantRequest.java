package de.fhdw.webshop.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.Map;

public record ProductVariantRequest(
        Long id,
        String sku,
        @DecimalMin("0.01") BigDecimal recommendedRetailPrice,
        @PositiveOrZero Integer stock,
        String imageUrl,
        Map<String, String> values
) {}
