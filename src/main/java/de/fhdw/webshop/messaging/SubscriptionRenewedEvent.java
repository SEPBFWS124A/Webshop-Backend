package de.fhdw.webshop.messaging;

import java.time.Instant;

/**
 * #136 — Published to RabbitMQ when a Webshop Plus subscription is successfully renewed.
 */
public record SubscriptionRenewedEvent(
        Long subscriptionId,
        Long userId,
        Instant nextPeriodEnd
) {}
