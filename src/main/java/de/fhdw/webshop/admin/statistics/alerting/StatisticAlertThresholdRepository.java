package de.fhdw.webshop.admin.statistics.alerting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StatisticAlertThresholdRepository extends JpaRepository<StatisticAlertThreshold, Long> {

    List<StatisticAlertThreshold> findAllByEnabledTrueOrderByMetricAsc();

    List<StatisticAlertThreshold> findAllByOrderByMetricAsc();

    Optional<StatisticAlertThreshold> findByMetric(StatisticMetric metric);
}
