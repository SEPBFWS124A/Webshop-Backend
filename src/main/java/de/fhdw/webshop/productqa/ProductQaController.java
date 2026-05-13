package de.fhdw.webshop.productqa;

import de.fhdw.webshop.productqa.dto.CreateProductAnswerRequest;
import de.fhdw.webshop.productqa.dto.CreateProductQuestionRequest;
import de.fhdw.webshop.helpfulvote.dto.HelpfulVoteRequest;
import de.fhdw.webshop.productqa.dto.ProductAnswerResponse;
import de.fhdw.webshop.productqa.dto.ProductQuestionResponse;
import de.fhdw.webshop.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products/{productId}/questions")
@RequiredArgsConstructor
public class ProductQaController {

    private final ProductQaService productQaService;

    @GetMapping
    public ResponseEntity<List<ProductQuestionResponse>> listQuestions(@PathVariable Long productId,
                                                                       @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(productQaService.listQuestions(productId, currentUser));
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ProductQuestionResponse> createQuestion(
            @PathVariable Long productId,
            @Valid @RequestBody CreateProductQuestionRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(productQaService.createQuestion(productId, currentUser, request));
    }

    @PostMapping("/{questionId}/answers")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ProductQuestionResponse> createAnswer(
            @PathVariable Long productId,
            @PathVariable Long questionId,
            @Valid @RequestBody CreateProductAnswerRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(productQaService.createAnswer(productId, questionId, currentUser, request));
    }

    @PostMapping("/{questionId}/answers/{answerId}/vote")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ProductAnswerResponse> voteAnswer(
            @PathVariable Long productId,
            @PathVariable Long questionId,
            @PathVariable Long answerId,
            @Valid @RequestBody HelpfulVoteRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(productQaService.voteAnswer(
                productId,
                questionId,
                answerId,
                currentUser,
                request.helpful()
        ));
    }
}
