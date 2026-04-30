package de.fhdw.webshop.admin.statistics.alerting.dto;

import de.fhdw.webshop.admin.statistics.alerting.StatisticMetric;

import java.math.BigDecimal;

public record StatisticAlertThresholdRequest(
        StatisticMetric metric,
        Integer periodDays,
        BigDecimal deviationPercent,
        Boolean enabled
) {}
