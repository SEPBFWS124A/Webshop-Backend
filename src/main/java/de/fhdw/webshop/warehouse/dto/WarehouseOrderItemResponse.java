package de.fhdw.webshop.warehouse.dto;

public record WarehouseOrderItemResponse(
        Long productId,
        String productName,
        int quantity,
        int availableStock,
        int warehouseStock,
        String warehousePosition
) {}
