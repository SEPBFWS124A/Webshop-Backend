package de.fhdw.webshop.returnrequest.dto;

public record ReturnRequestItemResponse(
        Long id,
        Long orderItemId,
        String productName,
        int quantity
) {}
