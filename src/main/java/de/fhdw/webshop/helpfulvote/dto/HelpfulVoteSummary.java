package de.fhdw.webshop.helpfulvote.dto;

public record HelpfulVoteSummary(
        long helpfulCount,
        long notHelpfulCount,
        long helpfulScore,
        Boolean currentUserVote
) {
}
