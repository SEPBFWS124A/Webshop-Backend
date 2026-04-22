package de.fhdw.webshop.followuporder.dto;

import de.fhdw.webshop.followuporder.FollowUpOrderStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record FollowUpOrderResponse(
    Long id,
    LocalDate executionDate,
    FollowUpOrderStatus status,
    Long sourceOrderId,
    Instant createdAt,
    List<FollowUpOrderItemResponse> items
) {}
