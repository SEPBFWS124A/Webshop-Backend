package de.fhdw.webshop.admin.statistics.alerting.dto;

import de.fhdw.webshop.admin.statistics.alerting.StatisticAlertThreshold;
import de.fhdw.webshop.admin.statistics.alerting.StatisticMetric;

import java.math.BigDecimal;
import java.time.Instant;

public record StatisticAlertThresholdResponse(
        Long id,
        StatisticMetric metric,
        String metricLabel,
        int periodDays,
        BigDecimal deviationPercent,
        boolean enabled,
        Instant updatedAt
) {
    public static StatisticAlertThresholdResponse from(StatisticAlertThreshold threshold) {
        return new StatisticAlertThresholdResponse(
                threshold.getId(),
                threshold.getMetric(),
                threshold.getMetric().getLabel(),
                threshold.getPeriodDays(),
                threshold.getDeviationPercent(),
                threshold.isEnabled(),
                threshold.getUpdatedAt()
        );
    }
}
