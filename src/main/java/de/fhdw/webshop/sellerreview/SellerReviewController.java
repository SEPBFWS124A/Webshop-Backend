package de.fhdw.webshop.sellerreview;

import de.fhdw.webshop.sellerreview.dto.CreateSellerReviewRequest;
import de.fhdw.webshop.sellerreview.dto.SellerReviewResponse;
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
@RequiredArgsConstructor
public class SellerReviewController {

    private final SellerReviewService sellerReviewService;

    @GetMapping("/api/seller-reviews/my")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<List<SellerReviewResponse>> listMyReviews(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(sellerReviewService.listMyReviews(currentUser));
    }

    @GetMapping("/api/orders/{orderId}/seller-reviews")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<List<SellerReviewResponse>> listMyReviewsForOrder(
            @PathVariable Long orderId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(sellerReviewService.listMyReviewsForOrder(orderId, currentUser));
    }

    @PostMapping("/api/orders/{orderId}/seller-reviews")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<SellerReviewResponse> createReview(
            @PathVariable Long orderId,
            @Valid @RequestBody CreateSellerReviewRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(sellerReviewService.createReview(orderId, currentUser, request));
    }
}
