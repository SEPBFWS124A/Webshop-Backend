package de.fhdw.webshop.tradein.dto;

import de.fhdw.webshop.tradein.TradeInCondition;
import de.fhdw.webshop.tradein.TradeInStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record TradeInResponse(
        Long id,
        Long customerId,
        String customerName,
        String customerEmail,
        Long orderId,
        Long orderItemId,
        String productName,
        TradeInCondition condition,
        TradeInStatus status,
        BigDecimal estimatedValue,
        String couponCode,
        Instant createdAt,
        Instant updatedAt
) {}
