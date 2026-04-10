package de.fhdw.webshop.notification.dto;

import jakarta.validation.constraints.NotBlank;

public record SendEmailRequest(
        @NotBlank String subject,
        @NotBlank String body
) {}
