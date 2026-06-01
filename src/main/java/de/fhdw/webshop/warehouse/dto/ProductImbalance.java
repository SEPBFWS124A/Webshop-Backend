package de.fhdw.webshop.warehouse.dto;

import java.util.Map;

public record ProductImbalance(
        Long productId,
        String productName,
        Map<Long, Integer> stockByWarehouse,
        int targetPerWarehouse,
        int maxDeviation
) {}

