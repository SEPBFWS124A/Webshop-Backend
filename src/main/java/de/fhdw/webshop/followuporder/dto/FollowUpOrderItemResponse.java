package de.fhdw.webshop.followuporder.dto;

public record FollowUpOrderItemResponse(
    Long id,
    Long productId,
    String productName,
    int quantity
) {}
