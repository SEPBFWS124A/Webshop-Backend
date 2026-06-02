package de.fhdw.webshop.cartreminder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CartReminderScheduler {

    private final CartReminderService cartReminderService;

    /** US #325 — Sends one reminder for abandoned carts after the configured waiting period. */
    @Scheduled(cron = "${cart-reminders.cron:0 15 * * * *}")
    public void sendCartReminders() {
        int sent = cartReminderService.sendDueReminders();
        if (sent > 0) {
            log.info("Sent {} cart reminder email(s)", sent);
        }
    }
}
