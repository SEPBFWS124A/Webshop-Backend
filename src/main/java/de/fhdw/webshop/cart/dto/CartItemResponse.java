package de.fhdw.webshop.cart.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record CartItemResponse(
        Long cartItemId,
        Long productId,
        String productName,
        Map<String, String> variantValues,
        String personalizationText,
        BigDecimal giftCardAmount,
        String giftCardRecipientEmail,
        String giftCardMessage,
        String imageUrl,
        BigDecimal unitPrice,
        BigDecimal co2EmissionKg,
        int availableStock,
        int quantity,
        BigDecimal lineTotal,
        BigDecimal lineCo2EmissionKg,
        Instant addedAt
) {}
