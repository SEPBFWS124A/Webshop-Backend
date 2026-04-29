package de.fhdw.webshop.admin.statistics.alerting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface StatisticAlertWarningRepository extends JpaRepository<StatisticAlertWarning, Long> {

    boolean existsByThresholdAndPeriodStartAndPeriodEnd(
            StatisticAlertThreshold threshold,
            LocalDate periodStart,
            LocalDate periodEnd
    );

    List<StatisticAlertWarning> findAllByStatusNotOrderByCreatedAtDesc(StatisticAlertStatus status);

    List<StatisticAlertWarning> findAllByOrderByCreatedAtDesc();
}
