package de.fhdw.webshop.productai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GenerateContentRequest(
        @NotBlank(message = "Stichpunkte dürfen nicht leer sein")
        @Size(max = 2000, message = "Stichpunkte dürfen maximal 2000 Zeichen haben")
        String keywords,

        String category
) {}
