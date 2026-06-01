package de.fhdw.webshop.warehouse.dto;

import de.fhdw.webshop.order.OrderStatus;

public record AdvanceWarehouseOrderRequest(
        /** Target status — mandatory for PUT /advance. */
        OrderStatus nextStatus,
        /** Required when transitioning PACKED_IN_WAREHOUSE → IN_TRUCK. */
        String truckIdentifier,
        /** Used internally by legacy warehouse/truck endpoints. */
        Long warehouseLocationId,
        /** Optional delivery confirmation latitude (SHIPPED → DELIVERED). */
        Double latitude,
        /** Optional delivery confirmation longitude (SHIPPED → DELIVERED). */
        Double longitude
) {}
