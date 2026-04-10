package de.fhdw.webshop.order.dto;

import de.fhdw.webshop.order.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id,
        OrderStatus status,
        BigDecimal totalPrice,
        BigDecimal taxAmount,
        BigDecimal shippingCost,
        String couponCode,
        Instant createdAt,
        List<OrderItemResponse> items
) {}
