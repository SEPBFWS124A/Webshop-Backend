package de.fhdw.webshop.standingorder.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record CreateStandingOrderRequest(
        @Min(1) int intervalDays,
        @NotNull LocalDate firstExecutionDate,
        @NotEmpty List<StandingOrderItemRequest> items
) {}
