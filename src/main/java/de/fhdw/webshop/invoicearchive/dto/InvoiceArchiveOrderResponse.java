package de.fhdw.webshop.invoicearchive.dto;

import de.fhdw.webshop.order.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record InvoiceArchiveOrderResponse(
        Long id,
        String orderNumber,
        Long requesterId,
        String requesterName,
        String requesterEmail,
        String customerNumber,
        Instant createdAt,
        OrderStatus status,
        BigDecimal totalPrice,
        BigDecimal taxAmount,
        int itemCount,
        String invoiceNumber
) {}
