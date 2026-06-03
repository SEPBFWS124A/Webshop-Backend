package de.fhdw.webshop.pricealert;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreatePriceAlertRequest(
        @NotNull @DecimalMin(value = "0.01", message = "targetPrice muss größer als 0 sein")
        BigDecimal targetPrice,
        boolean notifyByEmail
) {}

