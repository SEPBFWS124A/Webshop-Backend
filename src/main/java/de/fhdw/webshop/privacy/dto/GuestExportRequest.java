package de.fhdw.webshop.privacy.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record GuestExportRequest(
        @NotBlank @Email String email
) {}
