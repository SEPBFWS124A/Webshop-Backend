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
        String sharedWishlistToken,
        String sharedWishlistListId,
        Long bundleId,
        String bundleTitle,
        String bundleGroupKey,
        BigDecimal bundleDiscountPercent,
        String imageUrl,
        BigDecimal unitPrice,
        BigDecimal recommendedRetailPrice,
        BigDecimal co2EmissionKg,
        int availableStock,
        int reservedQuantity,
        Instant reservationExpiresAt,
        long reservationSecondsRemaining,
        String reservationStatus,
        int quantity,
        BigDecimal lineTotal,
        BigDecimal lineCo2EmissionKg,
        Instant addedAt,
        boolean restricted,
        String restrictionType
) {}
