package de.fhdw.webshop.messaging;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * #129 — Event published to RabbitMQ when an order is successfully placed.
 * Consumers (e-mail, analytics, fulfillment) receive this via the order.created queue.
 */
public record OrderCreatedEvent(
        Long orderId,
        String orderNumber,
        Long customerId,
        String customerEmail,
        BigDecimal totalPrice,
        int itemCount,
        Instant occurredAt
) {}
