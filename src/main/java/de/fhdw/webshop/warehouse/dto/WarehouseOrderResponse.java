package de.fhdw.webshop.warehouse.dto;

import de.fhdw.webshop.order.OrderStatus;
import java.time.Instant;
import java.util.List;

public record WarehouseOrderResponse(
        Long id,
        String orderNumber,
        String customerName,
        String customerEmail,
        OrderStatus status,
        String truckIdentifier,
        Instant createdAt,
        List<WarehouseOrderItemResponse> items
) {}
