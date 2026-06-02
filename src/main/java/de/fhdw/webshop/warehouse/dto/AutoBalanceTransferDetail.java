package de.fhdw.webshop.warehouse.dto;

public record AutoBalanceTransferDetail(
        Long productId,
        String productName,
        Long fromWarehouseId,
        String fromWarehouseName,
        Long toWarehouseId,
        String toWarehouseName,
        int quantity,
        Long orderId,
        String orderNumber
) {}

