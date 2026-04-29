package de.fhdw.webshop.admin.statistics.alerting;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
@Slf4j
public class StatisticAlertScheduler {

    private final StatisticAlertService statisticAlertService;

    @Scheduled(cron = "${app.statistics-alerts.cron:0 15 7 * * *}")
    public void evaluateStatisticAlerts() {
        int createdWarnings = statisticAlertService.evaluateEnabledThresholds(LocalDate.now(ZoneOffset.UTC)).size();
        if (createdWarnings > 0) {
            log.info("Created {} statistic alert warning(s)", createdWarnings);
        }
    }
}
