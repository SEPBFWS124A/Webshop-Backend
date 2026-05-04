package de.fhdw.webshop.order.dto;

import de.fhdw.webshop.order.OrderStatus;
import de.fhdw.webshop.order.ShippingMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderApprovalResponse(
        Long id,
        String orderNumber,
        OrderStatus status,
        Long requesterId,
        String requesterName,
        String requesterEmail,
        Instant createdAt,
        BigDecimal totalPrice,
        BigDecimal taxAmount,
        BigDecimal shippingCost,
        ShippingMethod shippingMethod,
        BigDecimal discountAmount,
        String couponCode,
        String approvalReason,
        BigDecimal approvalBudgetLimit,
        String rejectionReason,
        Instant decidedAt,
        List<OrderItemResponse> items,
        Boolean confirmationEmailSent
) {}
