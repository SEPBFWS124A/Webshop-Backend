package de.fhdw.webshop.affiliate.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record AffiliateLinkResponse(
        Long id,
        String trackingCode,
        Long productId,
        String productName,
        String productImageUrl,
        int clickCount,
        boolean active,
        Instant createdAt,
        int conversionCount,
        BigDecimal totalRevenue,
        BigDecimal totalCommission
) {}
