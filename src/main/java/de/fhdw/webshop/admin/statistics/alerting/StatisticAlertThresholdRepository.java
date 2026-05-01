package de.fhdw.webshop.admin.statistics.alerting;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface StatisticAlertThresholdRepository extends JpaRepository<StatisticAlertThreshold, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT t
            FROM StatisticAlertThreshold t
            WHERE t.enabled = true
            ORDER BY t.metric ASC
            """)
    List<StatisticAlertThreshold> findAllEnabledForAlertEvaluation();

    List<StatisticAlertThreshold> findAllByOrderByMetricAsc();

    Optional<StatisticAlertThreshold> findByMetric(StatisticMetric metric);
}
