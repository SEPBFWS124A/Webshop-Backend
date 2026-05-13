package de.fhdw.webshop.productqa;

import de.fhdw.webshop.notification.SystemNotificationService;
import de.fhdw.webshop.product.Product;
import de.fhdw.webshop.product.ProductRepository;
import de.fhdw.webshop.productqa.dto.CreateProductAnswerRequest;
import de.fhdw.webshop.productqa.dto.CreateProductQuestionRequest;
import de.fhdw.webshop.productqa.dto.ProductAnswerResponse;
import de.fhdw.webshop.productqa.dto.ProductQuestionResponse;
import de.fhdw.webshop.user.User;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductQaService {

    private final ProductQuestionRepository questionRepository;
    private final ProductAnswerRepository answerRepository;
    private final ProductRepository productRepository;
    private final ProfanityFilterService profanityFilterService;
    private final SystemNotificationService notificationService;

    @Transactional(readOnly = true)
    public List<ProductQuestionResponse> listQuestions(Long productId) {
        return questionRepository.findByProductIdOrderByCreatedAtDesc(productId)
                .stream()
                .map(this::toResponse)
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

        return toResponse(questionRepository.save(question));
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

        return toResponse(question);
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

    private ProductQuestionResponse toResponse(ProductQuestion question) {
        return new ProductQuestionResponse(
                question.getId(),
                question.getProduct().getId(),
                question.getAuthor().getId(),
                question.getAuthor().getUsername(),
                question.getQuestionText(),
                question.getCreatedAt(),
                question.getAnswers().stream().map(this::toResponse).toList()
        );
    }

    private ProductAnswerResponse toResponse(ProductAnswer answer) {
        return new ProductAnswerResponse(
                answer.getId(),
                answer.getAuthor().getId(),
                answer.getAuthor().getUsername(),
                answer.getAnswerText(),
                answer.getCreatedAt()
        );
    }
}
