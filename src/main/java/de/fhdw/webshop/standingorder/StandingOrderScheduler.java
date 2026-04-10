package de.fhdw.webshop.standingorder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * US #55 — Runs every day at 06:00 and triggers all standing orders that are due.
 * Customers receive their recurring order automatically without any manual action.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StandingOrderScheduler {

    private final StandingOrderService standingOrderService;

    @Scheduled(cron = "0 0 6 * * *")
    public void executeAllDueStandingOrders() {
        log.info("Standing order scheduler started");
        standingOrderService.executeAllDue();
        log.info("Standing order scheduler finished");
    }
}
