package de.fhdw.webshop.warehouse.dto;

import java.time.Instant;

public record WarehouseOrderItemResponse(
        Long itemId,
        Long productId,
        String productName,
        String personalizationText,
        int quantity,
        int availableStock,
        int warehouseStock,
        String warehousePosition,
        Instant pickedAt,
        String pickedByUserId,
        Integer pickedQuantity
) {}
