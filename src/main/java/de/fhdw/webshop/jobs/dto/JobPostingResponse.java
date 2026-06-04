package de.fhdw.webshop.jobs.dto;

import java.time.Instant;

public record JobPostingResponse(
        Long id,
        String title,
        String description,
        String employmentType,
        String location,
        String status,
        int displayOrder,
        Instant createdAt,
        Instant updatedAt
) {}
