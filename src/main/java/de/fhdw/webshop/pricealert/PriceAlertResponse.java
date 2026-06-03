package de.fhdw.webshop.pricealert;

import java.math.BigDecimal;
import java.time.Instant;

public record PriceAlertResponse(
        Long id,
        Long productId,
        String productName,
        BigDecimal targetPrice,
        BigDecimal currentPrice,
        boolean active,
        PriceAlertStatus status,
        boolean notifyByEmail,
        Instant createdAt,
        Instant triggeredAt
) {}

