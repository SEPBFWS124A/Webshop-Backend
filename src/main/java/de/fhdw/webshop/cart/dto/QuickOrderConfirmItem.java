package de.fhdw.webshop.cart.dto;

public record QuickOrderConfirmItem(
        Integer lineNumber,
        String articleNumber,
        Integer quantity
) {}
