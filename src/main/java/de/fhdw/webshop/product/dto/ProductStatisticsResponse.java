package de.fhdw.webshop.product.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** US #34 — Sales statistics for a specific product over a date range. */
public record ProductStatisticsResponse(
        Long productId,
        LocalDate from,
        LocalDate to,
        long unitsSold,
        BigDecimal totalRevenue
) {}
