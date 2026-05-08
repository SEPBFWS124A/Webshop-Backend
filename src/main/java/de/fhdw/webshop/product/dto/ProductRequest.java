package de.fhdw.webshop.product.dto;

import de.fhdw.webshop.product.ProductEcoScore;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;

public record ProductRequest(
        @NotBlank String name,
        String description,
        String imageUrl,
        @NotNull @DecimalMin("0.01") BigDecimal recommendedRetailPrice,
        @NotNull @DecimalMin("0.001") BigDecimal co2EmissionKg,
        ProductEcoScore ecoScore,
        String category,
        @PositiveOrZero Integer stock,
        String sku,
        Boolean purchasable,
        Boolean personalizable,
        @PositiveOrZero Integer personalizationMaxLength,
        boolean hasVariants,
        List<ProductVariantAttributeRequest> variantAttributes,
        List<ProductVariantRequest> variants
) {}
