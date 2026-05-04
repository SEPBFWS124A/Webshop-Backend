package de.fhdw.webshop.order.dto;

import java.math.BigDecimal;
import java.util.List;

public record OrderPreviewResponse(
        String orderNumber,
        String confirmationEmail,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        BigDecimal shippingCost,
        BigDecimal climateContributionAmount,
        BigDecimal totalCo2EmissionKg,
        BigDecimal totalPrice,
        String couponCode,
        Boolean approvalRequired,
        BigDecimal approvalBudgetLimit,
        List<OrderPreviewItemResponse> items
) {}
