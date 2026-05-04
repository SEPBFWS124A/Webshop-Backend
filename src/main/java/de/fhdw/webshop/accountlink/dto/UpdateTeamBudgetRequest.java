package de.fhdw.webshop.accountlink.dto;

import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;

public record UpdateTeamBudgetRequest(
        @DecimalMin(value = "0.00", inclusive = true)
        BigDecimal maxOrderValueLimit
) {}
