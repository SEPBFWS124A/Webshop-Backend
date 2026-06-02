package de.fhdw.webshop.warehouse.dto;

import de.fhdw.webshop.warehouse.TruckStatus;
import java.time.Instant;

public record WarehouseTruckResponse(
        String truckIdentifier,
        TruckStatus status,
        WarehouseLocationResponse originWarehouseLocation,
        WarehouseLocationResponse currentWarehouseLocation,
        String driverId,
        Instant departureTime,
        Instant completedAt,
        String routeOptimizationId,
        Double currentLatitude,
        Double currentLongitude,
        int capacityOrders,
        int assignedOrderCount,
        int remainingCapacity,
        boolean onRoute,
        String locationLabel
) {}

