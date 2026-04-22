package de.fhdw.webshop.recommendation;

import de.fhdw.webshop.recommendation.dto.ProductRecommendationListResponse;
import de.fhdw.webshop.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class ProductRecommendationController {

    private final ProductRecommendationService productRecommendationService;

    @GetMapping("/products/{productId}")
    public ResponseEntity<ProductRecommendationListResponse> getProductRecommendations(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "4") Integer limit,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(productRecommendationService.getRecommendationsForProduct(productId, currentUser, limit));
    }

    @GetMapping("/cart")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ProductRecommendationListResponse> getCartRecommendations(
            @RequestParam(defaultValue = "4") Integer limit,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(productRecommendationService.getRecommendationsForCart(currentUser, limit));
    }
}
