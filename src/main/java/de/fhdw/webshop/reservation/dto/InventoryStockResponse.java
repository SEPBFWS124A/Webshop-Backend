package de.fhdw.webshop.reservation.dto;

import java.time.Instant;
import java.util.Map;

public record InventoryStockResponse(
        Long productId,
        String productName,
        String sku,
        String category,
        int totalStock,
        int reservedStock,
        int availableStock,
        Map<Long, Integer> stockByLocation,
        Instant nextReservationExpiresAt
) {}
