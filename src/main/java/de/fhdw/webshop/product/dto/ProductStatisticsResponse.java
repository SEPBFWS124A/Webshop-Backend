package de.fhdw.webshop.product.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** US #34, #89 — Sales statistics for a specific product over a date range. */
public record ProductStatisticsResponse(
        Long productId,
        LocalDate from,
        LocalDate to,
        long unitsSold,
        BigDecimal totalRevenue,
        long standardPriceSalesCount,
        long discountedSalesCount,
        List<PriceSoldEntry> priceBreakdown
) {}
