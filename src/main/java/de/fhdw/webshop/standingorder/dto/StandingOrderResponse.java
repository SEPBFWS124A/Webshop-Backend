package de.fhdw.webshop.standingorder.dto;

import de.fhdw.webshop.standingorder.IntervalType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record StandingOrderResponse(
    Long id,
    IntervalType intervalType,
    Integer intervalValue,
    Integer dayOfWeek,
    Integer dayOfMonth,
    Integer monthOfYear,
    boolean countBackwards,
    LocalDate nextExecutionDate,
    boolean active,
    boolean notificationsEnabled,
    Instant createdAt,
    List<StandingOrderItemResponse> items
) {}