package de.fhdw.webshop.order.dto;

import de.fhdw.webshop.order.OrderStatus;
import de.fhdw.webshop.order.ShippingMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id,
        String orderNumber,
        String confirmationEmail,
        OrderStatus status,
        BigDecimal totalPrice,
        BigDecimal taxAmount,
        BigDecimal shippingCost,
        ShippingMethod shippingMethod,
        BigDecimal discountAmount,
        String couponCode,
        Instant createdAt,
        List<OrderItemResponse> items,
        Boolean confirmationEmailSent
) {}
