package de.fhdw.webshop.invoicearchive.dto;

import java.math.BigDecimal;

public record InvoiceArchiveOrderItemResponse(
        Long productId,
        String productName,
        String sellerName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal taxRate,
        BigDecimal lineTotal) {
}
