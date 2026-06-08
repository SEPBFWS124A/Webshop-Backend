package de.fhdw.webshop.newsletter.dto;

import java.time.Instant;

public record NewsletterSubscriptionResponse(
        Long categoryId,
        String categoryName,
        String categorySlug,
        boolean subscribed,
        Instant updatedAt
) {}
