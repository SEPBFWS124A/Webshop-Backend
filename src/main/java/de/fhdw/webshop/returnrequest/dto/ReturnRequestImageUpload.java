package de.fhdw.webshop.returnrequest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ReturnRequestImageUpload(
        @NotBlank String fileName,
        @NotBlank String contentType,
        @Positive long sizeBytes,
        @NotNull String dataBase64
) {}
