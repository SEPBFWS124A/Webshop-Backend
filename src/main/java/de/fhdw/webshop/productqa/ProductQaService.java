package de.fhdw.webshop.productqa;

import de.fhdw.webshop.notification.SystemNotificationService;
import de.fhdw.webshop.helpfulvote.HelpfulVoteService;
import de.fhdw.webshop.helpfulvote.HelpfulVoteTargetType;
import de.fhdw.webshop.helpfulvote.dto.HelpfulVoteSummary;
import de.fhdw.webshop.product.Product;
import de.fhdw.webshop.product.ProductRepository;
import de.fhdw.webshop.productqa.dto.CreateProductAnswerRequest;
import de.fhdw.webshop.productqa.dto.CreateProductQuestionRequest;
import de.fhdw.webshop.productqa.dto.ProductAnswerResponse;
import de.fhdw.webshop.productqa.dto.ProductQuestionResponse;
import de.fhdw.webshop.user.User;
import de.fhdw.webshop.user.UserRole;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProductQaService {

    private static final Set<UserRole> OFFICIAL_ANSWER_ROLES =
            Set.of(UserRole.EMPLOYEE, UserRole.SALES_EMPLOYEE, UserRole.ADMIN);

    private final ProductQuestionRepository questionRepository;
    private final ProductAnswerRepository answerRepository;
    private final ProductRepository productRepository;
    private final ProfanityFilterService profanityFilterService;
    private final SystemNotificationService notificationService;
    private final HelpfulVoteService helpfulVoteService;

    @Transactional(readOnly = true)
    public List<ProductQuestionResponse> listQuestions(Long productId, User currentUser) {
        return questionRepository.findByProductIdOrderByCreatedAtDesc(productId)
                .stream()
                .map(question -> toResponse(question, currentUser))
                .toList();
    }

    @Transactional
    public ProductQuestionResponse createQuestion(
            Long productId,
            User currentUser,
            CreateProductQuestionRequest request
    ) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found: " + productId));
        String text = normalizeText(request.text());
        profanityFilterService.validateClean(text);

        ProductQuestion question = new ProductQuestion();
        question.setProduct(product);
        question.setAuthor(currentUser);
        question.setQuestionText(text);

        return toResponse(questionRepository.save(question), currentUser);
    }

    @Transactional
    public ProductQuestionResponse createAnswer(
            Long productId,
            Long questionId,
            User currentUser,
            CreateProductAnswerRequest request
    ) {
        ProductQuestion question = questionRepository.findById(questionId)
                .orElseThrow(() -> new EntityNotFoundException("Question not found: " + questionId));
        if (!question.getProduct().getId().equals(productId)) {
            throw new EntityNotFoundException("Question not found for product: " + questionId);
        }

        String text = normalizeText(request.text());
        profanityFilterService.validateClean(text);

        ProductAnswer answer = new ProductAnswer();
        answer.setQuestion(question);
        answer.setAuthor(currentUser);
        answer.setAnswerText(text);
        if (Boolean.TRUE.equals(request.officialAnswer())) {
            markOfficial(answer, currentUser);
        }
        answerRepository.save(answer);
        question.getAnswers().add(answer);

        User questionAuthor = question.getAuthor();
        if (!questionAuthor.getId().equals(currentUser.getId())) {
            notificationService.createProductQaAnswerNotification(
                    questionAuthor,
                    question.getProduct().getId(),
                    question.getProduct().getName(),
                    currentUser.getUsername()
            );
        }

        return toResponse(question, currentUser);
    }

    @Transactional
    public ProductAnswerResponse markOfficialAnswer(
            Long productId,
            Long questionId,
            Long answerId,
            User currentUser
    ) {
        ProductQuestion question = loadQuestionForProduct(productId, questionId);
        ProductAnswer answer = answerRepository.findById(answerId)
                .orElseThrow(() -> new EntityNotFoundException("Answer not found: " + answerId));
        if (!answer.getQuestion().getId().equals(question.getId())) {
            throw new EntityNotFoundException("Answer not found for question: " + answerId);
        }

        markOfficial(answer, currentUser);
        return toResponse(answerRepository.save(answer), currentUser);
    }

    @Transactional
    public ProductAnswerResponse voteAnswer(
            Long productId,
            Long questionId,
            Long answerId,
            User currentUser,
            boolean helpful
    ) {
        ProductQuestion question = loadQuestionForProduct(productId, questionId);

        ProductAnswer answer = answerRepository.findById(answerId)
                .orElseThrow(() -> new EntityNotFoundException("Answer not found: " + answerId));
        if (!answer.getQuestion().getId().equals(questionId)) {
            throw new EntityNotFoundException("Answer not found for question: " + answerId);
        }

        helpfulVoteService.toggleVote(HelpfulVoteTargetType.PRODUCT_QA_ANSWER, answerId, currentUser, helpful);
        return toResponse(answer, currentUser);
    }

    private String normalizeText(String value) {
        String normalized = String.valueOf(value).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Bitte gib einen Text ein.");
        }
        if (normalized.length() > 500) {
            throw new IllegalArgumentException("Der Text darf maximal 500 Zeichen lang sein.");
        }
        return normalized;
    }

    private ProductQuestion loadQuestionForProduct(Long productId, Long questionId) {
        ProductQuestion question = questionRepository.findById(questionId)
                .orElseThrow(() -> new EntityNotFoundException("Question not found: " + questionId));
        if (!question.getProduct().getId().equals(productId)) {
            throw new EntityNotFoundException("Question not found for product: " + questionId);
        }
        return question;
    }

    private void markOfficial(ProductAnswer answer, User currentUser) {
        if (!canMarkOfficial(currentUser)) {
            throw new IllegalArgumentException("Nur berechtigte Mitarbeiter können offizielle Antworten markieren.");
        }
        answer.setOfficialAnswer(true);
        answer.setOfficialMarkedByUser(currentUser);
        answer.setOfficialMarkedAt(Instant.now());
    }

    private boolean canMarkOfficial(User user) {
        return user != null
                && user.getRoles() != null
                && user.getRoles().stream().anyMatch(OFFICIAL_ANSWER_ROLES::contains);
    }

    private ProductQuestionResponse toResponse(ProductQuestion question, User currentUser) {
        return new ProductQuestionResponse(
                question.getId(),
                question.getProduct().getId(),
                question.getAuthor().getId(),
                question.getAuthor().getUsername(),
                question.getQuestionText(),
                question.getCreatedAt(),
                question.getAnswers().stream()
                        .map(answer -> toResponse(answer, currentUser))
                        .sorted((left, right) -> {
                            int officialCompare = Boolean.compare(right.officialAnswer(), left.officialAnswer());
                            if (officialCompare != 0) {
                                return officialCompare;
                            }
                            int scoreCompare = Long.compare(right.helpfulScore(), left.helpfulScore());
                            if (scoreCompare != 0) {
                                return scoreCompare;
                            }
                            return left.createdAt().compareTo(right.createdAt());
                        })
                        .toList()
        );
    }

    private ProductAnswerResponse toResponse(ProductAnswer answer, User currentUser) {
        HelpfulVoteSummary votes = helpfulVoteService.summarize(
                HelpfulVoteTargetType.PRODUCT_QA_ANSWER,
                answer.getId(),
                currentUser
        );
        return new ProductAnswerResponse(
                answer.getId(),
                answer.getAuthor().getId(),
                answer.getAuthor().getUsername(),
                answer.getAnswerText(),
                answer.getCreatedAt(),
                votes.helpfulCount(),
                votes.notHelpfulCount(),
                votes.helpfulScore(),
                votes.currentUserVote(),
                answer.isOfficialAnswer(),
                answer.getOfficialMarkedByUser() == null ? null : answer.getOfficialMarkedByUser().getId(),
                answer.getOfficialMarkedByUser() == null ? null : answer.getOfficialMarkedByUser().getUsername(),
                answer.getOfficialMarkedAt()
        );
    }
}
