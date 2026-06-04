package de.fhdw.webshop.newsletter;

import de.fhdw.webshop.newsletter.dto.*;
import de.fhdw.webshop.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminNewsletterController {

    private final NewsletterService newsletterService;

    @GetMapping("/api/admin/newsletter/posts")
    public ResponseEntity<List<NewsletterPostSummaryResponse>> getAllPosts() {
        return ResponseEntity.ok(newsletterService.getAllPostsForAdmin());
    }

    @GetMapping("/api/admin/newsletter/posts/{id}")
    public ResponseEntity<NewsletterPostResponse> getPost(@PathVariable Long id) {
        return ResponseEntity.ok(newsletterService.getPostForAdmin(id));
    }

    @GetMapping("/api/admin/newsletter/categories/stats")
    public ResponseEntity<List<NewsletterCategoryStatsResponse>> getCategoryStats() {
        return ResponseEntity.ok(newsletterService.getCategoryStats());
    }

    @PostMapping(value = "/api/admin/newsletter/posts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<NewsletterPostSummaryResponse> createPost(
            @RequestParam String title,
            @RequestParam String content,
            @RequestParam Long categoryId,
            @RequestParam(required = false) String scheduledPublishAt,
            @RequestPart(value = "images", required = false) List<MultipartFile> images,
            @AuthenticationPrincipal User currentUser) {
        Instant scheduled = parseInstant(scheduledPublishAt);
        CreateNewsletterPostRequest request = new CreateNewsletterPostRequest(title, content, categoryId, scheduled);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(newsletterService.createPost(request, images, currentUser));
    }

    @PutMapping(value = "/api/admin/newsletter/posts/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<NewsletterPostSummaryResponse> updatePost(
            @PathVariable Long id,
            @RequestParam String title,
            @RequestParam String content,
            @RequestParam Long categoryId,
            @RequestParam(required = false) String scheduledPublishAt,
            @RequestPart(value = "images", required = false) List<MultipartFile> images,
            @AuthenticationPrincipal User currentUser) {
        Instant scheduled = parseInstant(scheduledPublishAt);
        CreateNewsletterPostRequest request = new CreateNewsletterPostRequest(title, content, categoryId, scheduled);
        return ResponseEntity.ok(newsletterService.updatePost(id, request, images, currentUser));
    }

    @DeleteMapping("/api/admin/newsletter/posts/{id}")
    public ResponseEntity<Void> deletePost(@PathVariable Long id,
                                            @AuthenticationPrincipal User currentUser) {
        newsletterService.deletePost(id, currentUser);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/admin/newsletter/posts/{id}/publish")
    public ResponseEntity<NewsletterPostSummaryResponse> togglePublish(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(newsletterService.togglePublish(id, currentUser));
    }

    @PutMapping("/api/admin/newsletter/posts/reorder")
    public ResponseEntity<List<NewsletterPostSummaryResponse>> reorderPosts(
            @Valid @RequestBody ReorderNewsletterPostsRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(newsletterService.reorderPosts(request.orderedIds(), currentUser));
    }

    @DeleteMapping("/api/admin/newsletter/images/{imageId}")
    public ResponseEntity<Void> deleteImage(@PathVariable Long imageId,
                                             @AuthenticationPrincipal User currentUser) {
        newsletterService.deleteImage(imageId, currentUser);
        return ResponseEntity.noContent().build();
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            return null;
        }
    }
}
