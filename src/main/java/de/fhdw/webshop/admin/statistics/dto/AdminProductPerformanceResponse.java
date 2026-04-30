package de.fhdw.webshop.admin.statistics.dto;

import java.math.BigDecimal;

public record AdminProductPerformanceResponse(
        Long productId,
        String productName,
        String category,
        boolean purchasable,
        long unitsSold,
        BigDecimal revenue
) {}
