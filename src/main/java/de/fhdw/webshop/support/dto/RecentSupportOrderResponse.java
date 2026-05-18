package de.fhdw.webshop.support.dto;

import java.time.Instant;

public record RecentSupportOrderResponse(
        Long id,
        String orderNumber,
        Instant createdAt
) {
}
