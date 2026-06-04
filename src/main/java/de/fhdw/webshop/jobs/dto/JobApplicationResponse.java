package de.fhdw.webshop.jobs.dto;

import java.time.Instant;
import java.util.List;

public record JobApplicationResponse(
        Long id,
        Long jobPostingId,
        String jobPostingTitle,
        String applicantName,
        String applicantEmail,
        String applicantPhone,
        String motivationText,
        String status,
        Instant createdAt,
        Instant updatedAt,
        List<JobApplicationFileResponse> files
) {}
