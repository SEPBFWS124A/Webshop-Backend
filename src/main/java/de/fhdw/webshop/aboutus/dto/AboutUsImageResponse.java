package de.fhdw.webshop.aboutus.dto;

import java.time.Instant;

public record AboutUsImageResponse(
        Long id,
        String originalFilename,
        String contentType,
        long fileSizeBytes,
        String dataUrl,
        Instant createdAt
) {}
