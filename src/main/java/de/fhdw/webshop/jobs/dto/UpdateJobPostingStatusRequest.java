package de.fhdw.webshop.jobs.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateJobPostingStatusRequest(
        @NotBlank String status
) {}
