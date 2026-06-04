package de.fhdw.webshop.newsletter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NewsletterScheduler {

    private final NewsletterService newsletterService;

    @Scheduled(fixedRate = 3_600_000)
    public void publishScheduledPosts() {
        newsletterService.publishScheduledPosts();
        log.debug("Scheduled newsletter post check completed");
    }
}
