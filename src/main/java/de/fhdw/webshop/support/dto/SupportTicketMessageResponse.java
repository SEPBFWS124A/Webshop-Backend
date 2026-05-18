package de.fhdw.webshop.support.dto;

import java.time.Instant;

public record SupportTicketMessageResponse(
        Long id,
        Long authorId,
        String authorName,
        String message,
        boolean internalNote,
        Instant createdAt
) {
}
