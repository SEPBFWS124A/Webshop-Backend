package de.fhdw.webshop.product.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
        Long id,
        String name,
        String description,
        String imageUrl,
        BigDecimal recommendedRetailPrice,
        String category,
        boolean purchasable,
        boolean promoted,
        Instant createdAt
) {}
