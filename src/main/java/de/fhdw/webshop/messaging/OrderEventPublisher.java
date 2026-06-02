package de.fhdw.webshop.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * #129 — Publishes domain events to RabbitMQ.
 * All publish calls are fire-and-forget: a failure must never
 * block or roll back the primary business transaction.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishOrderCreated(OrderCreatedEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE,
                    RabbitMQConfig.ORDER_CREATED_ROUTING_KEY,
                    event
            );
            log.debug("Published order.created event for order {} ({})",
                    event.orderNumber(), event.orderId());
        } catch (Exception e) {
            log.error("Failed to publish order.created event for order {}: {}",
                    event.orderNumber(), e.getMessage());
        }
    }
}
