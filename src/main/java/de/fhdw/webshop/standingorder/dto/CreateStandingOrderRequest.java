package de.fhdw.webshop.standingorder.dto;

import de.fhdw.webshop.standingorder.IntervalType;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public record CreateStandingOrderRequest(
    @NotNull IntervalType intervalType,
    @NotNull Integer intervalValue,
    Integer dayOfWeek,
    Integer dayOfMonth,
    Integer monthOfYear,
    boolean countBackwards,
    @NotNull LocalDate firstExecutionDate,
    List<StandingOrderItemRequest> items
) {}