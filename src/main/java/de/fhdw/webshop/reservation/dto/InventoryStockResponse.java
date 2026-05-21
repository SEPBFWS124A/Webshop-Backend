package de.fhdw.webshop.reservation.dto;

import java.time.Instant;

public record InventoryStockResponse(
        Long productId,
        String productName,
        String sku,
        String category,
        int totalStock,
        int reservedStock,
        int availableStock,
        Instant nextReservationExpiresAt
) {}
