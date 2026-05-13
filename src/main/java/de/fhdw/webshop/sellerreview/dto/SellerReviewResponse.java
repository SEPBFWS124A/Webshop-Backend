package de.fhdw.webshop.sellerreview.dto;

import java.time.Instant;

public record SellerReviewResponse(
        Long id,
        Long orderId,
        String orderNumber,
        String sellerName,
        int rating,
        String comment,
        Instant createdAt
) {
}
