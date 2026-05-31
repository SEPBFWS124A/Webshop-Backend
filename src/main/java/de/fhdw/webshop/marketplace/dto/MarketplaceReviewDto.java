package de.fhdw.webshop.marketplace.dto;

import java.time.Instant;

public record MarketplaceReviewDto(
        Long id,
        int rating,
        String comment,
        Instant createdAt
) {
}
