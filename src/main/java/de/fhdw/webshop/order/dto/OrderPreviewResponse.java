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
        BigDecimal totalPrice,
        String couponCode,
        List<OrderPreviewItemResponse> items
) {}
