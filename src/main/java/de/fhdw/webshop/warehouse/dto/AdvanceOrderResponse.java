package de.fhdw.webshop.warehouse.dto;

import de.fhdw.webshop.order.OrderStatus;
import java.time.Instant;

/**
 * Response returned by PUT /api/warehouse/orders/{id}/advance.
 * The {@code batchUpdatedCount} is > 1 when an IN_TRUCK → SHIPPED transition
 * advances all orders on the same truck simultaneously.
 */
public record AdvanceOrderResponse(
        boolean success,
        OrderSummary order,
        String message,
        int batchUpdatedCount
) {
    public record OrderSummary(
            Long id,
            OrderStatus status,
            String truckIdentifier,
            Instant updatedAt
    ) {}
}

