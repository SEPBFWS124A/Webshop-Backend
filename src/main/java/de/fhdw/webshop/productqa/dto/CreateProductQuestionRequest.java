package de.fhdw.webshop.productqa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateProductQuestionRequest(
        @NotBlank(message = "Bitte gib eine Frage ein.")
        @Size(max = 500, message = "Die Frage darf maximal 500 Zeichen lang sein.")
        String text
) {
}
