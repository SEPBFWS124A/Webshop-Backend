package de.fhdw.webshop.standingorder.dto;

public record StandingOrderItemResponse(
        Long id,
        Long productId,
        String productName,
        int quantity
) {}
