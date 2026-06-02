package de.fhdw.webshop.product;

import de.fhdw.webshop.product.dto.ProductFeedbackDto;
import de.fhdw.webshop.user.User;
import de.fhdw.webshop.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ProductReviewController {

    private final ProductFeedbackRepository productFeedbackRepository;
    private final ProductFeedbackValidationService validationService;
    private final UserRepository userRepository;

    @GetMapping("/api/products/{productId}/feedback")
    public ResponseEntity<Map<String, Object>> listFeedback(@PathVariable Long productId) {
        List<ProductFeedbackDto> reviews = productFeedbackRepository
                .findByProductIdAndApprovedTrueOrderByCreatedAtDesc(productId)
                .stream()
                .map(f -> new ProductFeedbackDto(
                        f.getId(),
                        null,
                        resolveUsername(f.getUserId()),
                        f.getRating(),
                        f.getComment(),
                        f.getCreatedAt(),
                        f.getSource()
                ))
                .toList();

        ProductFeedbackStatsResponse stats = validationService.getStats(productId);

        return ResponseEntity.ok(Map.of(
                "reviews", reviews,
                "avgRating", stats.avgRating(),
                "reviewCount", stats.reviewCount()
        ));
    }

    @GetMapping("/api/products/{productId}/feedback/my")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ProductFeedbackDto> getMyFeedback(
            @PathVariable Long productId,
            @AuthenticationPrincipal User currentUser) {

        return productFeedbackRepository.findByProductIdOrderByCreatedAtDesc(productId)
                .stream()
                .filter(f -> currentUser.getId().equals(f.getUserId()))
                .findFirst()
                .map(f -> ResponseEntity.ok(new ProductFeedbackDto(
                        f.getId(),
                        null,
                        currentUser.getUsername(),
                        f.getRating(),
                        f.getComment(),
                        f.getCreatedAt(),
                        f.getSource()
                )))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/api/products/{productId}/feedback")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ProductFeedback> createFeedback(
            @PathVariable Long productId,
            @RequestBody ProductFeedbackRequest request,
            @AuthenticationPrincipal User currentUser) {

        validationService.assertExternalSeller(productId);
        validationService.assertVerifiedPurchase(productId, currentUser.getId());

        if (productFeedbackRepository.existsByProductIdAndUserId(productId, currentUser.getId())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        ProductFeedback feedback = new ProductFeedback();
        feedback.setProductId(productId);
        feedback.setUserId(currentUser.getId());
        feedback.setRating(request.rating());
        feedback.setComment(request.comment());
        feedback.setSource("MARKETPLACE");

        return ResponseEntity.status(HttpStatus.CREATED).body(productFeedbackRepository.save(feedback));
    }

    private String resolveUsername(Long userId) {
        if (userId == null) return "Anonym";
        return userRepository.findById(userId)
                .map(User::getUsername)
                .orElse("Anonym");
    }
}
