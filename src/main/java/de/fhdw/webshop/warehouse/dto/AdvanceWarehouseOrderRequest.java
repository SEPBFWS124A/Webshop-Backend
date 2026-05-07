package de.fhdw.webshop.warehouse.dto;

public record AdvanceWarehouseOrderRequest(
        String truckIdentifier,
        Long warehouseLocationId
) {}
