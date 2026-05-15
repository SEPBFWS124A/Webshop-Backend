package de.fhdw.webshop.discount.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record VolumeDiscountTierRequest(
        @NotBlank @Size(max = 160) String name,
        @DecimalMin(value = "0.01") BigDecimal minOrderValue,
        @Min(1) Integer minItemCount,
        @NotNull @DecimalMin(value = "0.01") @DecimalMax(value = "100.00") BigDecimal discountPercent,
        @NotNull Boolean active
) {
}
