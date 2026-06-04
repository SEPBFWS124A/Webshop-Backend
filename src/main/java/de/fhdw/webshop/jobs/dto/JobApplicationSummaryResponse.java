package de.fhdw.webshop.jobs.dto;

import java.time.Instant;

public record JobApplicationSummaryResponse(
        Long id,
        Long jobPostingId,
        String jobPostingTitle,
        String applicantName,
        String applicantEmail,
        String status,
        Instant createdAt
) {}
