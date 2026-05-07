package de.fhdw.webshop.order.dto;

import de.fhdw.webshop.order.OrderStatus;
import de.fhdw.webshop.order.ShippingMethod;
import de.fhdw.webshop.pickup.dto.PickupStoreResponse;

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
        BigDecimal climateContributionAmount,
        BigDecimal totalCo2EmissionKg,
        BigDecimal discountAmount,
        String couponCode,
        Instant createdAt,
        Instant deliveredAt,
        List<OrderItemResponse> items,
        String truckIdentifier,
        Instant estimatedDeliveryAt,
        String approvalReason,
        BigDecimal approvalBudgetLimit,
        PickupStoreResponse pickupStore,
        Boolean confirmationEmailSent
) {}
