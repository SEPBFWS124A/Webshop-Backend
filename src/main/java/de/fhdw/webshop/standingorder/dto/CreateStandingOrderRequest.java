package de.fhdw.webshop.standingorder.dto;

import de.fhdw.webshop.standingorder.IntervalType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public record CreateStandingOrderRequest(
        @NotNull IntervalType intervalType,
        Integer intervalDays,
        Integer dayOfWeek,
        Integer dayOfMonth,
        Integer monthOfYear,
        @NotNull LocalDate firstExecutionDate,
        @NotEmpty List<StandingOrderItemRequest> items
) {}