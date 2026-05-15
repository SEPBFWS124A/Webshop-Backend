package de.fhdw.webshop.order.dto;

import java.math.BigDecimal;
import java.util.Map;

public record OrderItemResponse(
        Long orderItemId,
        Long productId,
        String productName,
        String sellerName,
        Map<String, String> variantValues,
        String personalizationText,
        BigDecimal giftCardAmount,
        String giftCardRecipientEmail,
        String giftCardMessage,
        String giftCardCode,
        String sharedWishlistToken,
        String sharedWishlistListId,
        boolean productPurchasable,
        int quantity,
        BigDecimal priceAtOrderTime,
        BigDecimal lineTotal
) {}
