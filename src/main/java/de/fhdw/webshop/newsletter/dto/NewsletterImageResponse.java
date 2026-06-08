package de.fhdw.webshop.newsletter.dto;

import java.time.Instant;

public record NewsletterImageResponse(
        Long id,
        String originalFilename,
        String contentType,
        long fileSizeBytes,
        String dataUrl,
        Instant createdAt
) {}
