package de.fhdw.webshop.warehouse.dto;

public record TruckAssignmentChangeResponse(
        Long orderId,
        String orderNumber,
        String regionLabel,
        String previousTruckIdentifier,
        String newTruckIdentifier
) {}
