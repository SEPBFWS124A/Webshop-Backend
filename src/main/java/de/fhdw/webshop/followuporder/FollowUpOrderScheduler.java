package de.fhdw.webshop.followuporder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class FollowUpOrderScheduler {

    private final FollowUpOrderService followUpOrderService;

    @Scheduled(cron = "0 0 6 * * *")
    public void executeAllDue() {
        log.info("Follow-up order scheduler started");
        followUpOrderService.executeAllDue();
        log.info("Follow-up order scheduler finished");
    }
}
