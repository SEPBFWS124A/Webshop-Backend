package de.fhdw.webshop.admin.statistics.alerting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StatisticAlertThresholdRepository extends JpaRepository<StatisticAlertThreshold, Long> {

    List<StatisticAlertThreshold> findAllByEnabledTrueOrderByMetricLabelAsc();

    List<StatisticAlertThreshold> findAllByOrderByMetricLabelAsc();

    boolean existsByMetricLabelIgnoreCase(String metricLabel);

    boolean existsByMetricLabelIgnoreCaseAndIdNot(String metricLabel, Long id);
}
