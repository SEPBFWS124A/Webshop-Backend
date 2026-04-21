package de.fhdw.webshop.order.dto;

import java.math.BigDecimal;

public record OrderPreviewItemResponse(
        Long productId,
        String productName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal
) {}
