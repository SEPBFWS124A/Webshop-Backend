package de.fhdw.webshop.alerting.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AddKnownEmailRequest(
        @NotBlank String label,
        @NotBlank @Email String email
) {}
