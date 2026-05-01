package de.fhdw.webshop.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpdateCo2EmissionRequest(
        @NotNull @DecimalMin("0.001") BigDecimal co2EmissionKg
) {}
