package de.fhdw.webshop.order.dto;

import java.math.BigDecimal;
import java.util.Map;

public record OrderPreviewItemResponse(
        Long productId,
        String productName,
        Map<String, String> variantValues,
        String personalizationText,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal
) {}
