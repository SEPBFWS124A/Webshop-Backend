package de.fhdw.webshop.warehouse.dto;

import java.time.Instant;
import java.util.Map;

public record StockOverviewItem(
        Long productId,
        String productName,
        String sku,
        int totalStock,
        int reservedStock,
        int availableStock,
        Map<Long, Integer> stockByLocation,
        Instant nextReservationExpiresAt
) {}

