package de.fhdw.webshop.quote.dto;

import java.math.BigDecimal;

public record QuoteRequestItemResponse(
        Long id,
        Long productId,
        String productName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal
) {}
