package de.fhdw.webshop.quote.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record QuoteRequestResponse(
        Long id,
        String quoteNumber,
        Instant createdAt,
        Instant validUntil,
        String status,
        boolean expired,
        String notes,
        String companyName,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        BigDecimal shippingCost,
        BigDecimal totalPrice,
        String pdfUrl,
        List<QuoteRequestItemResponse> items
) {}
