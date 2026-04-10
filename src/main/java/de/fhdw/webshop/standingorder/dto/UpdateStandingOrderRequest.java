package de.fhdw.webshop.standingorder.dto;

import jakarta.validation.constraints.Min;

import java.util.List;

public record UpdateStandingOrderRequest(
        @Min(1) int intervalDays,
        List<StandingOrderItemRequest> items
) {}
