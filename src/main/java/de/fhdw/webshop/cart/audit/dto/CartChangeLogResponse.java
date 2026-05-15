package de.fhdw.webshop.cart.audit.dto;

import de.fhdw.webshop.cart.audit.CartChangeAction;
import java.time.Instant;

public record CartChangeLogResponse(
        Long id,
        Long actorUserId,
        String actorUsername,
        Long customerId,
        String customerNumber,
        Long cartItemId,
        Long productId,
        String productSku,
        String productName,
        CartChangeAction action,
        int quantityDelta,
        int resultingQuantity,
        Instant createdAt,
        String message
) {}
