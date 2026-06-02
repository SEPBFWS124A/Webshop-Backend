package de.fhdw.webshop.affiliate.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CommissionRateRequest(
        @NotNull
        @DecimalMin("0.00")
        @DecimalMax("1.00")
        BigDecimal commissionRate
) {}
