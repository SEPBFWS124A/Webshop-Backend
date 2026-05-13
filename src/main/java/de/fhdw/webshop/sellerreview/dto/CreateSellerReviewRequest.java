package de.fhdw.webshop.sellerreview.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateSellerReviewRequest(
        @NotBlank(message = "Bitte einen Verkaeufer aus der Bestellung auswaehlen.")
        @Size(max = 180, message = "Der Verkaeufername darf maximal 180 Zeichen lang sein.")
        String sellerName,

        @NotNull(message = "Bitte eine Sternebewertung angeben.")
        @Min(value = 1, message = "Die Bewertung muss mindestens 1 Stern enthalten.")
        @Max(value = 5, message = "Die Bewertung darf maximal 5 Sterne enthalten.")
        Integer rating,

        @Size(max = 1000, message = "Der Kommentar darf maximal 1000 Zeichen lang sein.")
        String comment
) {
}
