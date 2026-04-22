package de.fhdw.webshop.standingorder.dto;

import de.fhdw.webshop.standingorder.IntervalType;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UpdateStandingOrderRequest(
    @NotNull IntervalType intervalType,
    @NotNull Integer intervalValue,
    Integer dayOfWeek,
    Integer dayOfMonth,
    Integer monthOfYear,
    boolean countBackwards,
    List<StandingOrderItemRequest> items
) {}
