package de.fhdw.webshop.productqa.dto;

import java.time.Instant;

public record ProductAnswerResponse(
        Long id,
        Long authorId,
        String authorName,
        String text,
        Instant createdAt,
        long helpfulCount,
        long notHelpfulCount,
        long helpfulScore,
        Boolean currentUserVote,
        boolean officialAnswer,
        Long officialMarkedByUserId,
        String officialMarkedByUserName,
        Instant officialMarkedAt
) {
}
