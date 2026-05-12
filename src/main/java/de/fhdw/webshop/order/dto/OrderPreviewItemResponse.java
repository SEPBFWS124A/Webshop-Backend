package de.fhdw.webshop.order.dto;

import java.math.BigDecimal;
import java.util.Map;

public record OrderPreviewItemResponse(
        Long productId,
        String productName,
        Map<String, String> variantValues,
        String personalizationText,
        BigDecimal giftCardAmount,
        String giftCardRecipientEmail,
        String giftCardMessage,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal
) {}
