package de.fhdw.webshop.affiliate;

import de.fhdw.webshop.affiliate.dto.*;
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
@RequestMapping("/api/affiliate")
@RequiredArgsConstructor
public class AffiliateController {

    private final AffiliateService affiliateService;

    // ── Bewerbung ─────────────────────────────────────────────────────────────

    @PostMapping("/apply")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<AffiliateApplicationResponse> apply(
            @Valid @RequestBody AffiliateApplicationRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(affiliateService.applyForAffiliate(user, request.motivationText()));
    }

    @GetMapping("/application/status")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<AffiliateApplicationResponse> getStatus(
            @AuthenticationPrincipal User user) {
        AffiliateApplicationResponse response = affiliateService.getApplicationStatus(user);
        return response != null ? ResponseEntity.ok(response) : ResponseEntity.noContent().build();
    }

    // ── Links ─────────────────────────────────────────────────────────────────

    @PostMapping("/links")
    @PreAuthorize("hasRole('AFFILIATE_CUSTOMER')")
    public ResponseEntity<AffiliateLinkResponse> generateLink(
            @Valid @RequestBody AffiliateLinkRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(affiliateService.generateLink(user, request.productId()));
    }

    @GetMapping("/links")
    @PreAuthorize("hasRole('AFFILIATE_CUSTOMER')")
    public ResponseEntity<List<AffiliateLinkResponse>> getLinks(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(affiliateService.getLinksWithStats(user));
    }

    @DeleteMapping("/links/{id}")
    @PreAuthorize("hasRole('AFFILIATE_CUSTOMER')")
    public ResponseEntity<Void> deactivateLink(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        affiliateService.deactivateLink(user, id);
        return ResponseEntity.noContent().build();
    }

    // ── Dashboard-Statistiken ────────────────────────────────────────────────

    @GetMapping("/stats")
    @PreAuthorize("hasRole('AFFILIATE_CUSTOMER')")
    public ResponseEntity<AffiliateDashboardStats> getStats(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(affiliateService.getDashboardStats(user));
    }

    @GetMapping("/conversions")
    @PreAuthorize("hasRole('AFFILIATE_CUSTOMER')")
    public ResponseEntity<List<AffiliateConversionResponse>> getConversions(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(affiliateService.getConversions(user));
    }

    // ── Click-Tracking (public) ───────────────────────────────────────────────

    @GetMapping("/track/{code}")
    public ResponseEntity<TrackClickResponse> trackClick(@PathVariable String code) {
        return ResponseEntity.ok(affiliateService.trackClick(code));
    }
}
