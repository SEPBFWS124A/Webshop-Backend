package de.fhdw.webshop.warehouse.dto;

public record WarehouseTransferResponse(
        Long productId,
        String productName,
        WarehouseLocationResponse fromWarehouse,
        WarehouseLocationResponse toWarehouse,
        int transferredQuantity,
        int fromStockAfter,
        int toStockAfter
) {}
