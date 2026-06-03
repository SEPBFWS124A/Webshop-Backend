package de.fhdw.webshop.subscription;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * #136 — Daily job that renews or expires due Webshop Plus subscriptions.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionScheduler {

    private final SubscriptionService subscriptionService;

    @Scheduled(cron = "0 0 6 * * *")
    public void processSubscriptions() {
        log.info("SubscriptionScheduler: starting daily renewal run");
        try {
            subscriptionService.processDueSubscriptions();
        } catch (Exception e) {
            log.error("SubscriptionScheduler: renewal run failed: {}", e.getMessage(), e);
        }
    }
}
