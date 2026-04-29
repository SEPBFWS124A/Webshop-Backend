package de.fhdw.webshop.admin.statistics.alerting.dto;

import de.fhdw.webshop.admin.statistics.alerting.StatisticAlertStatus;
import de.fhdw.webshop.admin.statistics.alerting.StatisticAlertWarning;
import de.fhdw.webshop.admin.statistics.alerting.StatisticMetric;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record StatisticAlertWarningResponse(
        Long id,
        StatisticMetric metric,
        String metricLabel,
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalDate comparisonStart,
        LocalDate comparisonEnd,
        BigDecimal currentValue,
        BigDecimal comparisonValue,
        BigDecimal deviationPercent,
        String reason,
        StatisticAlertStatus status,
        Instant createdAt,
        Instant readAt,
        Instant resolvedAt
) {
    public static StatisticAlertWarningResponse from(StatisticAlertWarning warning) {
        return new StatisticAlertWarningResponse(
                warning.getId(),
                warning.getMetric(),
                warning.getMetricLabel(),
                warning.getPeriodStart(),
                warning.getPeriodEnd(),
                warning.getComparisonStart(),
                warning.getComparisonEnd(),
                warning.getCurrentValue(),
                warning.getComparisonValue(),
                warning.getDeviationPercent(),
                warning.getReason(),
                warning.getStatus(),
                warning.getCreatedAt(),
                warning.getReadAt(),
                warning.getResolvedAt()
        );
    }
}
