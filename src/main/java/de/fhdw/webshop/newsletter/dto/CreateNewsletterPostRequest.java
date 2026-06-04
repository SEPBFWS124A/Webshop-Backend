package de.fhdw.webshop.newsletter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateNewsletterPostRequest(
        @NotBlank @Size(max = 255) String title,
        @NotBlank String content,
        @NotNull Long categoryId,
        Instant scheduledPublishAt
) {}
