package de.fhdw.webshop.order.dto;

import java.math.BigDecimal;
import java.util.Map;

public record OrderItemResponse(
        Long orderItemId,
        Long productId,
        String productName,
        Map<String, String> variantValues,
        boolean productPurchasable,
        int quantity,
        BigDecimal priceAtOrderTime,
        BigDecimal lineTotal
) {}
