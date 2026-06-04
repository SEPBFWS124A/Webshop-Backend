package de.fhdw.webshop.jobs.dto;

import jakarta.validation.constraints.NotBlank;

public record SendDecisionEmailRequest(
        @NotBlank String customEmailText
) {}
