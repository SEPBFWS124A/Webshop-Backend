package de.fhdw.webshop.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ProductRequest(
        @NotBlank String name,
        String description,
        String imageUrl,
        @NotNull @DecimalMin("0.01") BigDecimal recommendedRetailPrice,
        String category
) {}
