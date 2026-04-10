package de.fhdw.webshop.discount.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateDiscountRequest(
        @NotNull Long productId,
        @NotNull @DecimalMin("0.01") @DecimalMax("100.00") BigDecimal discountPercent,
        @NotNull LocalDate validFrom,
        LocalDate validUntil   // null = unlimited (US #54)
) {}
