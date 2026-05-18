package de.fhdw.webshop.sellerreview.dto;

import java.time.Instant;
import java.util.List;

public record SellerReviewResponse(
        Long id,
        Long orderId,
        String orderNumber,
        String sellerName,
        int rating,
        String comment,
        Instant createdAt,
        long helpfulCount,
        long notHelpfulCount,
        long helpfulScore,
        Boolean currentUserVote,
        List<SellerReviewImageResponse> images
) {
}
