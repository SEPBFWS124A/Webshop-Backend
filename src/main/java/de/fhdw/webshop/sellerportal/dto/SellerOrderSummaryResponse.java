package de.fhdw.webshop.sellerportal.dto;

import de.fhdw.webshop.order.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record SellerOrderSummaryResponse(
        Long orderId,
        String orderNumber,
        String customerName,
        OrderStatus status,
        Instant orderedAt,
        Instant deliveredAt,
        int itemCount,
        int quantity,
        BigDecimal grossAmount,
        BigDecimal discountAmount,
        BigDecimal commissionAmount,
        BigDecimal feeAmount,
        BigDecimal openReturnHoldbackAmount,
        BigDecimal settledReturnAmount,
        BigDecimal netAmount,
        boolean hasActiveReturn,
        boolean hasSettledReturn,
        boolean payoutBlocked,
        boolean cancelled
) {
}
