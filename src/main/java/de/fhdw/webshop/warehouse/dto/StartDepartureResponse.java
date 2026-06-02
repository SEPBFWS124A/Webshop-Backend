package de.fhdw.webshop.warehouse.dto;

import java.time.Instant;
import java.util.List;

/**
 * Response for POST /api/warehouse/trucks/{truckId}/start-departure.
 * Contains the result of a truck departure including route information.
 */
public record StartDepartureResponse(
        boolean success,
        String truckIdentifier,
        int shippedOrderCount,
        int skippedOrderCount,
        Instant departureTime,
        List<ShippedOrderSummary> shippedOrders,
        List<SkippedOrderSummary> skippedOrders,
        String message
) {
    public record ShippedOrderSummary(
            Long orderId,
            String orderNumber,
            String deliveryCity,
            String deliveryPostalCode
    ) {}

    public record SkippedOrderSummary(
            Long orderId,
            String orderNumber,
            String reason
    ) {}
}

