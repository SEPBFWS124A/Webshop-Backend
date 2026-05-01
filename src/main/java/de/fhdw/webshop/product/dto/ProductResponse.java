package de.fhdw.webshop.product.dto;

import de.fhdw.webshop.product.ProductEcoScore;

import java.math.BigDecimal;
import java.time.Instant;

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
        boolean purchasable,
        boolean promoted,
        Instant createdAt
) {}
