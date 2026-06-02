package de.fhdw.webshop.admin.marketplace.dto;

import java.time.Instant;

public record AdminSellerReviewDto(
        Long id,
        String sellerName,
        Long customerId,
        String customerName,
        String orderNumber,
        int rating,
        String comment,
        Instant createdAt,
        Boolean approved
) {}
