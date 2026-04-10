package de.fhdw.webshop.customer;

import java.math.BigDecimal;
import java.time.LocalDate;

/** US #29 — Revenue statistics for a customer over a date range. */
public record RevenueStatisticsResponse(
        Long customerId,
        LocalDate from,
        LocalDate to,
        long orderCount,
        BigDecimal totalRevenue
) {}
