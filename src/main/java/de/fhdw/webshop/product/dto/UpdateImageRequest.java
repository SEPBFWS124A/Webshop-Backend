package de.fhdw.webshop.product.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateImageRequest(
        @NotBlank String imageUrl
) {}
