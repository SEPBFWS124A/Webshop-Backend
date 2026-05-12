package de.fhdw.webshop.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record PlaceOrderItemRequest(
        @NotNull Long productId,
        @Min(1) int quantity,
        String personalizationText,
        BigDecimal giftCardAmount,
        String giftCardRecipientEmail,
        String giftCardMessage
) {}
