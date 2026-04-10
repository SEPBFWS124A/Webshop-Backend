package de.fhdw.webshop.cart.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record CartItemResponse(
        Long cartItemId,
        Long productId,
        String productName,
        String imageUrl,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal lineTotal,
        Instant addedAt
) {}
