package de.fhdw.webshop.helpfulvote;

import de.fhdw.webshop.helpfulvote.dto.HelpfulVoteSummary;
import de.fhdw.webshop.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HelpfulVoteService {

    private final HelpfulVoteRepository helpfulVoteRepository;

    @Transactional
    public HelpfulVoteSummary toggleVote(
            HelpfulVoteTargetType targetType,
            Long targetId,
            User currentUser,
            boolean helpful
    ) {
        helpfulVoteRepository.findByUserIdAndTargetTypeAndTargetId(currentUser.getId(), targetType, targetId)
                .ifPresentOrElse(existingVote -> {
                    if (existingVote.isHelpful() == helpful) {
                        helpfulVoteRepository.delete(existingVote);
                    } else {
                        existingVote.setHelpful(helpful);
                        helpfulVoteRepository.save(existingVote);
                    }
                }, () -> {
                    HelpfulVote vote = new HelpfulVote();
                    vote.setUser(currentUser);
                    vote.setTargetType(targetType);
                    vote.setTargetId(targetId);
                    vote.setHelpful(helpful);
                    helpfulVoteRepository.save(vote);
                });

        return summarize(targetType, targetId, currentUser);
    }

    @Transactional(readOnly = true)
    public HelpfulVoteSummary summarize(HelpfulVoteTargetType targetType, Long targetId, User currentUser) {
        long helpfulCount = helpfulVoteRepository.countByTargetTypeAndTargetIdAndHelpful(targetType, targetId, true);
        long notHelpfulCount = helpfulVoteRepository.countByTargetTypeAndTargetIdAndHelpful(targetType, targetId, false);
        Boolean currentUserVote = currentUser == null
                ? null
                : helpfulVoteRepository.findByUserIdAndTargetTypeAndTargetId(currentUser.getId(), targetType, targetId)
                        .map(HelpfulVote::isHelpful)
                        .orElse(null);

        return new HelpfulVoteSummary(helpfulCount, notHelpfulCount, helpfulCount - notHelpfulCount, currentUserVote);
    }
}
