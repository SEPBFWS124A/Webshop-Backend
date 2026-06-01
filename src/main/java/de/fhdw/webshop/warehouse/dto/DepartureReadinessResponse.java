package de.fhdw.webshop.warehouse.dto;

import de.fhdw.webshop.order.OrderStatus;
import java.util.List;

/**
 * Response for POST /api/warehouse/trucks/{truckId}/departure-readiness.
 * Provides information about whether a truck is ready to depart and which orders are on it.
 */
public record DepartureReadinessResponse(
        String truckIdentifier,
        boolean readyToDepart,
        int totalOrdersOnTruck,
        int readyOrders,
        int notReadyOrders,
        List<OrderReadinessDetail> orders,
        List<String> issues,
        String message
) {
    public record OrderReadinessDetail(
            Long orderId,
            String orderNumber,
            OrderStatus status,
            boolean ready,
            String deliveryCity,
            String deliveryPostalCode
    ) {}
}

