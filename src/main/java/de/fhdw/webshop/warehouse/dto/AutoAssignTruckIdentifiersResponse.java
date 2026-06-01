package de.fhdw.webshop.warehouse.dto;

import java.util.List;

public record AutoAssignTruckIdentifiersResponse(
        List<WarehouseOrderResponse> orders,
        List<TruckAssignmentChangeResponse> changes,
        int assignedCount,
        List<String> trucksUsed,
        List<AutoAssignTruckRouteResponse> routesCreated
) {}
