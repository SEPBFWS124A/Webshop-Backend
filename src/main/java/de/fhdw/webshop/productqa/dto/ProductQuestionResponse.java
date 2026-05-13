package de.fhdw.webshop.productqa.dto;

import java.time.Instant;
import java.util.List;

public record ProductQuestionResponse(
        Long id,
        Long productId,
        Long authorId,
        String authorName,
        String text,
        Instant createdAt,
        List<ProductAnswerResponse> answers
) {
}
