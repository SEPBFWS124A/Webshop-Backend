package de.fhdw.webshop.discount.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record VolumeDiscountTierResponse(
        Long id,
        String name,
        BigDecimal minOrderValue,
        Integer minItemCount,
        BigDecimal discountPercent,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
