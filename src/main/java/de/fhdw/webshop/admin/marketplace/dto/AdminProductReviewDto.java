package de.fhdw.webshop.admin.marketplace.dto;

import java.time.LocalDateTime;

public record AdminProductReviewDto(
        Long id,
        Long productId,
        String productTitle,
        Long userId,
        String userName,
        Integer rating,
        String comment,
        LocalDateTime createdAt,
        String source,
        Boolean approved
) {}
