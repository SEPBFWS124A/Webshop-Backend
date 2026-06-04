package de.fhdw.webshop.jobs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateJobPostingRequest(
        @NotBlank @Size(max = 255) String title,
        @NotBlank String description,
        @NotBlank @Size(max = 50) String employmentType,
        @NotBlank @Size(max = 255) String location,
        String status,
        int displayOrder
) {}
