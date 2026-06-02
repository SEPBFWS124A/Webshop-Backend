package de.fhdw.webshop.warehouse.dto;

public record AutoAssignTruckRouteResponse(
        String truckIdentifier,
        int stops,
        String distance,
        String duration,
        String routeOptimizationId
) {}

