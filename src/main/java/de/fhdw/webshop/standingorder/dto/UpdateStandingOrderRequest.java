package de.fhdw.webshop.standingorder.dto;

import de.fhdw.webshop.standingorder.IntervalType;
import java.util.List;

public record UpdateStandingOrderRequest(
        IntervalType intervalType,
        Integer intervalDays,
        Integer dayOfWeek,
        Integer dayOfMonth,
        Integer monthOfYear,
        List<StandingOrderItemRequest> items
) {}