package de.fhdw.webshop.affiliate.dto;

import de.fhdw.webshop.affiliate.AffiliateConversionStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record AffiliateConversionResponse(
        Long id,
        Long orderId,
        String productName,
        BigDecimal purchaseAmount,
        BigDecimal commissionAmount,
        AffiliateConversionStatus status,
        Instant createdAt
) {}
