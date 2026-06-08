package de.fhdw.webshop.privacy.dto;

import jakarta.validation.constraints.NotBlank;

public record RegisteredExportRequest(
        @NotBlank String password
) {}
