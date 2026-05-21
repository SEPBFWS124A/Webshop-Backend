package de.fhdw.webshop.invoicearchive.dto;

import de.fhdw.webshop.order.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record InvoiceArchiveOrderDetailResponse(
        Long id,
        String orderNumber,
        String invoiceNumber,
        Long requesterId,
        String requesterName,
        String requesterEmail,
        String customerNumber,
        Instant createdAt,
        OrderStatus status,
        String paymentMethod,
        String deliveryStreet,
        String deliveryPostalCode,
        String deliveryCity,
        String deliveryCountry,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal shippingCost,
        BigDecimal netAmount,
        BigDecimal taxAmount,
        BigDecimal totalPrice,
        int itemCount,
        List<InvoiceArchiveOrderItemResponse> items) {
}
