package de.fhdw.webshop.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpdatePriceRequest(
        @NotNull @DecimalMin("0.01") BigDecimal recommendedRetailPrice
) {}
