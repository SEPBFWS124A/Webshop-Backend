package de.fhdw.webshop.reservation.dto;

import java.time.Instant;

public record StockReservationResponse(
        Long id,
        Long userId,
        String username,
        String customerNumber,
        Long productId,
        String productName,
        String sku,
        Long cartItemId,
        int quantity,
        String status,
        Instant reservedUntil,
        long secondsRemaining,
        Instant createdAt,
        Instant updatedAt
) {}
