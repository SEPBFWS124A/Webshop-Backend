package de.fhdw.webshop.product.dto;

import de.fhdw.webshop.product.ProductEcoScore;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ProductResponse(
        Long id,
        String name,
        String description,
        String imageUrl,
        BigDecimal recommendedRetailPrice,
        BigDecimal co2EmissionKg,
        ProductEcoScore ecoScore,
        String category,
        int stock,
        String sku,
        boolean purchasable,
        boolean promoted,
        boolean personalizable,
        Integer personalizationMaxLength,
        boolean hasVariants,
        Long parentProductId,
        Map<String, String> variantValues,
        List<ProductVariantAttributeResponse> variantAttributes,
        List<ProductResponse> variants,
        Instant createdAt
) {}
