package de.fhdw.webshop.order.dto;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long orderItemId,
        Long productId,
        String productName,
        boolean productPurchasable,
        int quantity,
        BigDecimal priceAtOrderTime,
        BigDecimal lineTotal
) {}
