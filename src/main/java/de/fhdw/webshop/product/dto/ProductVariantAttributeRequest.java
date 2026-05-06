package de.fhdw.webshop.product.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record ProductVariantAttributeRequest(
        @NotBlank String name,
        List<String> values
) {}
