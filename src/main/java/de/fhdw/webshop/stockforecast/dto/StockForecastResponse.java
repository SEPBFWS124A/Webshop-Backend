package de.fhdw.webshop.stockforecast.dto;

public record StockForecastResponse(
        Long productId,
        String productName,
        String sku,
        int currentStock,
        double avgDailySales,
        int estimatedDaysRemaining,
        int warningThresholdDays,
        ForecastStatus status,
        String supplierName,
        int supplierLeadTimeDays
) {
    public enum ForecastStatus { OK, WARNING, CRITICAL }
}
