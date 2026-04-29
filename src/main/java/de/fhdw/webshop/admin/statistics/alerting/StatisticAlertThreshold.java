package de.fhdw.webshop.admin.statistics.alerting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "statistic_alert_thresholds")
@Getter
@Setter
@NoArgsConstructor
public class StatisticAlertThreshold {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private StatisticMetric metric;

    @Column(name = "metric_label", nullable = false, length = 100)
    private String metricLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "calculation_metric", nullable = false, length = 50)
    private StatisticMetric calculationMetric;

    @Column(name = "period_days", nullable = false)
    private int periodDays;

    @Column(name = "deviation_percent", nullable = false, precision = 7, scale = 2)
    private BigDecimal deviationPercent;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
