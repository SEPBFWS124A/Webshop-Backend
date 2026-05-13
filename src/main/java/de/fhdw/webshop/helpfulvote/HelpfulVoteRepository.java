package de.fhdw.webshop.helpfulvote;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HelpfulVoteRepository extends JpaRepository<HelpfulVote, Long> {

    long countByTargetTypeAndTargetIdAndHelpful(HelpfulVoteTargetType targetType, Long targetId, boolean helpful);

    Optional<HelpfulVote> findByUserIdAndTargetTypeAndTargetId(
            Long userId,
            HelpfulVoteTargetType targetType,
            Long targetId
    );
}
