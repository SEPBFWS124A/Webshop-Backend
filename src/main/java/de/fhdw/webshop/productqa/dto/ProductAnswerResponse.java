package de.fhdw.webshop.productqa.dto;

import java.time.Instant;

public record ProductAnswerResponse(
        Long id,
        Long authorId,
        String authorName,
        String text,
        Instant createdAt
) {
}
