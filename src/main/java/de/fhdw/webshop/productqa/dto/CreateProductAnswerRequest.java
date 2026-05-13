package de.fhdw.webshop.productqa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateProductAnswerRequest(
        @NotBlank(message = "Bitte gib eine Antwort ein.")
        @Size(max = 500, message = "Die Antwort darf maximal 500 Zeichen lang sein.")
        String text
) {
}
