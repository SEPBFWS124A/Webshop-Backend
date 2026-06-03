package de.fhdw.webshop.subscription;

import de.fhdw.webshop.messaging.SubscriptionRenewedEvent;
import de.fhdw.webshop.subscription.dto.SubscriptionResponse;
import de.fhdw.webshop.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {

    private static final String EXCHANGE = "webshop.events";
    private static final String RENEWED_KEY = "subscription.renewed";

    private final SubscriptionRepository subscriptionRepository;
    private final RabbitTemplate rabbitTemplate;

    /** #134 — Get the current user's active (or cancelled) subscription. */
    @Transactional(readOnly = true)
    public SubscriptionResponse getMySubscription(User user) {
        return subscriptionRepository
                .findByUserIdAndStatus(user.getId(), SubscriptionStatus.ACTIVE)
                .or(() -> subscriptionRepository.findByUserIdAndStatus(user.getId(), SubscriptionStatus.CANCELLED))
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kein Abonnement gefunden."));
    }

    /** #134 — Subscribe the user to Webshop Plus. */
    @Transactional
    public SubscriptionResponse subscribe(User user) {
        if (subscriptionRepository.existsByUserIdAndStatus(user.getId(), SubscriptionStatus.ACTIVE)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Du hast bereits ein aktives Webshop Plus-Abonnement.");
        }
        Instant now = Instant.now();
        Subscription subscription = new Subscription();
        subscription.setUser(user);
        subscription.setStartedAt(now);
        subscription.setCurrentPeriodStart(now);
        subscription.setCurrentPeriodEnd(now.plus(30, ChronoUnit.DAYS));
        return toResponse(subscriptionRepository.save(subscription));
    }

    /** #134 — Cancel automatic renewal; subscription stays active until period end. */
    @Transactional
    public SubscriptionResponse cancel(User user) {
        Subscription subscription = subscriptionRepository
                .findByUserIdAndStatus(user.getId(), SubscriptionStatus.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Kein aktives Abonnement gefunden."));
        subscription.setCancelledAt(Instant.now());
        subscription.setStatus(SubscriptionStatus.CANCELLED);
        return toResponse(subscriptionRepository.save(subscription));
    }

    /** #135 — True when the user has a currently active Plus subscription. */
    @Transactional(readOnly = true)
    public boolean hasActivePlusSubscription(Long userId) {
        return subscriptionRepository.existsByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE);
    }

    /** #136 — Called by scheduler: renew or expire due subscriptions. */
    @Transactional
    public void processDueSubscriptions() {
        List<Subscription> due = subscriptionRepository.findDueForRenewal(Instant.now());
        log.info("SubscriptionService: processing {} due subscription(s)", due.size());
        for (Subscription s : due) {
            if (s.getCancelledAt() != null) {
                s.setStatus(SubscriptionStatus.EXPIRED);
                subscriptionRepository.save(s);
                log.info("Subscription {} expired (was cancelled by user)", s.getId());
            } else {
                Instant newStart = s.getCurrentPeriodEnd();
                Instant newEnd = newStart.plus(30, ChronoUnit.DAYS);
                s.setCurrentPeriodStart(newStart);
                s.setCurrentPeriodEnd(newEnd);
                subscriptionRepository.save(s);
                publishRenewedEvent(s);
                log.info("Subscription {} renewed until {}", s.getId(), newEnd);
            }
        }
    }

    private void publishRenewedEvent(Subscription s) {
        try {
            rabbitTemplate.convertAndSend(EXCHANGE, RENEWED_KEY,
                    new SubscriptionRenewedEvent(s.getId(), s.getUser().getId(), s.getCurrentPeriodEnd()));
        } catch (Exception e) {
            log.error("Failed to publish subscription.renewed event for {}: {}", s.getId(), e.getMessage());
        }
    }

    private SubscriptionResponse toResponse(Subscription s) {
        return new SubscriptionResponse(
                s.getId(), s.getPlan(), s.getStatus(), s.getMonthlyPrice(),
                s.getStartedAt(), s.getCurrentPeriodStart(), s.getCurrentPeriodEnd(), s.getCancelledAt()
        );
    }
}
