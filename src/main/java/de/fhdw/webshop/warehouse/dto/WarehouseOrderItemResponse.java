package de.fhdw.webshop.warehouse.dto;

public record WarehouseOrderItemResponse(
        Long productId,
        String productName,
        String personalizationText,
        int quantity,
        int availableStock,
        int warehouseStock,
        String warehousePosition
) {}
