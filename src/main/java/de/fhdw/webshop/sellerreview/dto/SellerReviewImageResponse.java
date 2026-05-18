package de.fhdw.webshop.sellerreview.dto;

import java.time.Instant;

public record SellerReviewImageResponse(
        Long id,
        Long reviewId,
        String sellerName,
        String orderNumber,
        String customerName,
        String customerEmail,
        String originalFilename,
        String contentType,
        long fileSizeBytes,
        Instant createdAt,
        String dataUrl
) {
}
