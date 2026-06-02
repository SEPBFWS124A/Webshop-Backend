package de.fhdw.webshop.warehouse.dto;

import de.fhdw.webshop.order.OrderStatus;

public record CompletePackingResponse(
        Long orderId,
        OrderStatus newStatus,
        String assignedTruckIdentifier,
        Long routePlanId,
        boolean routePlanningTriggered,
        String message
) {}

