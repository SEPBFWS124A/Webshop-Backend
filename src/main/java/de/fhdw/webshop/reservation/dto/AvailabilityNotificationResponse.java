package de.fhdw.webshop.reservation.dto;

import java.time.Instant;

public record AvailabilityNotificationResponse(
        Long productId,
        String productName,
        boolean active,
        Instant createdAt,
        Instant notifiedAt
) {}
