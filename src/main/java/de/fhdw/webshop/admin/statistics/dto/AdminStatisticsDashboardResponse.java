package de.fhdw.webshop.admin.statistics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record AdminStatisticsDashboardResponse(
        LocalDate from,
        LocalDate to,
        BigDecimal revenue,
        long orderCount,
        long activeCustomerCount,
        List<AdminProductPerformanceResponse> productPerformance,
        boolean hasOrderData
) {}
