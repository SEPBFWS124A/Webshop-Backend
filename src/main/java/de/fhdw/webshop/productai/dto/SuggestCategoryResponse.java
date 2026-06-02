package de.fhdw.webshop.productai.dto;

import java.util.List;

public record SuggestCategoryResponse(
        String category,
        List<String> tags
) {}
