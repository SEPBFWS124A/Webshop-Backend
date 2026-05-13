package de.fhdw.webshop.helpfulvote.dto;

import jakarta.validation.constraints.NotNull;

public record HelpfulVoteRequest(
        @NotNull(message = "Bitte gib an, ob der Beitrag hilfreich war.")
        Boolean helpful
) {
}
