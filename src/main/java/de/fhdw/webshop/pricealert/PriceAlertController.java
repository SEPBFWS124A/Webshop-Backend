package de.fhdw.webshop.pricealert;

import de.fhdw.webshop.user.User;
import jakarta.validation.Valid;
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
public class PriceAlertController {

    private final PriceAlertService priceAlertService;

    /** Neuen Preisalarm für ein Produkt anlegen. */
    @PostMapping("/api/products/{productId}/price-alerts")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> createPriceAlert(
            @PathVariable Long productId,
            @Valid @RequestBody CreatePriceAlertRequest request,
            @AuthenticationPrincipal User currentUser) {
        try {
            PriceAlertResponse response = priceAlertService.createPriceAlert(productId, request, currentUser);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (DuplicatePriceAlertException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("code", "DUPLICATE", "message", e.getMessage()));
        }
    }

    /** Alle Preisalarme des eingeloggten Users abrufen. */
    @GetMapping("/api/users/me/price-alerts")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<List<PriceAlertResponse>> getMyPriceAlerts(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(priceAlertService.getAlertsForUser(currentUser));
    }

    /** Preisalarm aktualisieren (targetPrice, active, notifyByEmail). */
    @PatchMapping("/api/price-alerts/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<PriceAlertResponse> updatePriceAlert(
            @PathVariable Long id,
            @RequestBody UpdatePriceAlertRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(priceAlertService.updatePriceAlert(id, request, currentUser));
    }

    /** Preisalarm löschen. */
    @DeleteMapping("/api/price-alerts/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Void> deletePriceAlert(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {
        priceAlertService.deletePriceAlert(id, currentUser);
        return ResponseEntity.noContent().build();
    }
}

