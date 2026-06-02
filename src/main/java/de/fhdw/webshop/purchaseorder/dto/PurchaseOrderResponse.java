package de.fhdw.webshop.purchaseorder.dto;

import de.fhdw.webshop.purchaseorder.PurchaseOrderStatus;

import java.time.Instant;

public record PurchaseOrderResponse(
        Long id,
        Long productId,
        String productName,
        String productSku,
        int quantity,
        String supplierName,
        PurchaseOrderStatus status,
        Integer aiSuggestedQuantity,
        Instant orderedAt,
        Instant receivedAt
) {}
