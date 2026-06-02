package de.fhdw.webshop.productai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SuggestCategoryRequest(
        @NotBlank(message = "Produktname oder -beschreibung darf nicht leer sein")
        @Size(max = 2000)
        String text
) {}
