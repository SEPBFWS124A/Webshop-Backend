package de.fhdw.webshop.newsletter.dto;

import jakarta.validation.constraints.NotNull;

public record NewsletterSubscriptionRequest(
        @NotNull Long categoryId,
        boolean subscribed
) {}
