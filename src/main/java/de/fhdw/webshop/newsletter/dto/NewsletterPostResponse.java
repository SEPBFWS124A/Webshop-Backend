package de.fhdw.webshop.newsletter.dto;

import java.time.Instant;
import java.util.List;

public record NewsletterPostResponse(
        Long id,
        String title,
        String content,
        NewsletterCategoryResponse category,
        String authorName,
        boolean published,
        Instant publishedAt,
        Instant scheduledPublishAt,
        int displayOrder,
        Instant createdAt,
        Instant updatedAt,
        List<NewsletterImageResponse> images
) {}
