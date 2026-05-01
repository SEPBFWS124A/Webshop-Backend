package de.fhdw.webshop.cart.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record CartItemResponse(
        Long cartItemId,
        Long productId,
        String productName,
        String imageUrl,
        BigDecimal unitPrice,
        BigDecimal co2EmissionKg,
        int availableStock,
        int quantity,
        BigDecimal lineTotal,
        BigDecimal lineCo2EmissionKg,
        Instant addedAt
) {}
