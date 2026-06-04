package de.fhdw.webshop.newsletter;

import de.fhdw.webshop.newsletter.dto.*;
import de.fhdw.webshop.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class NewsletterController {

    private final NewsletterService newsletterService;

    @GetMapping("/api/newsletter/categories")
    public ResponseEntity<List<NewsletterCategoryResponse>> getCategories() {
        return ResponseEntity.ok(newsletterService.getCategories());
    }

    @GetMapping("/api/newsletter/posts")
    public ResponseEntity<List<NewsletterPostSummaryResponse>> getPosts(
            @RequestParam(required = false) String category) {
        return ResponseEntity.ok(newsletterService.getPublishedPosts(category));
    }

    @GetMapping("/api/newsletter/posts/{id}")
    public ResponseEntity<NewsletterPostResponse> getPost(@PathVariable Long id) {
        return ResponseEntity.ok(newsletterService.getPublishedPost(id));
    }

    @GetMapping("/api/newsletter/my-subscriptions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<NewsletterSubscriptionResponse>> getMySubscriptions(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(newsletterService.getSubscriptions(currentUser));
    }

    @PostMapping("/api/newsletter/subscriptions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NewsletterSubscriptionResponse> updateSubscription(
            @Valid @RequestBody NewsletterSubscriptionRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(newsletterService.updateSubscription(currentUser, request));
    }
}
