package de.fhdw.webshop.cart.dto;

import java.util.List;

public record QuickOrderConfirmRequest(
        List<QuickOrderConfirmItem> items
) {}
