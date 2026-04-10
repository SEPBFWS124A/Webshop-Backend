package de.fhdw.webshop.product.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateDescriptionRequest(
        @NotBlank String description
) {}
