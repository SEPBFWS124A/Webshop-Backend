package de.fhdw.webshop.jobs.dto;

import java.time.Instant;

public record JobApplicationFileResponse(
        Long id,
        String originalFilename,
        String contentType,
        long fileSizeBytes,
        String fileType,
        String dataBase64,
        Instant createdAt
) {}
