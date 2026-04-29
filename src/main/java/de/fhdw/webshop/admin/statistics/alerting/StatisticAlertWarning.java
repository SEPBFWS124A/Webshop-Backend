package de.fhdw.webshop.admin.statistics.alerting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "statistic_alert_warnings")
@Getter
@Setter
@NoArgsConstructor
public class StatisticAlertWarning {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "threshold_id", nullable = false)
    private StatisticAlertThreshold threshold;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private StatisticMetric metric;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "comparison_start", nullable = false)
    private LocalDate comparisonStart;

    @Column(name = "comparison_end", nullable = false)
    private LocalDate comparisonEnd;

    @Column(name = "current_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal currentValue;

    @Column(name = "comparison_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal comparisonValue;

    @Column(name = "deviation_percent", nullable = false, precision = 8, scale = 2)
    private BigDecimal deviationPercent;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatisticAlertStatus status = StatisticAlertStatus.OPEN;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}
