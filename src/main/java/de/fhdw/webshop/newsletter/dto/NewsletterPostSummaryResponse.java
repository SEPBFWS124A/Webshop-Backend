package de.fhdw.webshop.newsletter.dto;

import java.time.Instant;

public record NewsletterPostSummaryResponse(
        Long id,
        String title,
        NewsletterCategoryResponse category,
        String authorName,
        boolean published,
        Instant publishedAt,
        Instant scheduledPublishAt,
        int displayOrder,
        Instant createdAt,
        Instant updatedAt,
        String previewImageDataUrl
) {}
