package de.fhdw.webshop.product.dto;

import java.util.List;

public record ProductVariantAttributeResponse(
        String name,
        List<String> values
) {}
