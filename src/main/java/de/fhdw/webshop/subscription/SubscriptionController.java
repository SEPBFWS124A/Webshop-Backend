package de.fhdw.webshop.subscription;

import de.fhdw.webshop.subscription.dto.SubscriptionResponse;
import de.fhdw.webshop.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    /** #134 — Get the current user's subscription status. */
    @GetMapping("/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<SubscriptionResponse> getMySubscription(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(subscriptionService.getMySubscription(user));
    }

    /** #134 — Subscribe to Webshop Plus. */
    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<SubscriptionResponse> subscribe(@AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(subscriptionService.subscribe(user));
    }

    /** #134 — Cancel automatic renewal (subscription stays active until period end). */
    @PutMapping("/me/cancel")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<SubscriptionResponse> cancel(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(subscriptionService.cancel(user));
    }
}
