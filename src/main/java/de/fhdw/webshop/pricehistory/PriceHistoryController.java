package de.fhdw.webshop.pricehistory;

import de.fhdw.webshop.user.User;
import de.fhdw.webshop.user.UserRole;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/api/products/{productId}/price-history")
@RequiredArgsConstructor
public class PriceHistoryController {

    private final ProductPriceHistoryService historyService;

    /**
     * GET /api/products/{productId}/price-history
     * Öffentlich zugänglich – Admins erhalten zusätzliche Felder.
     *
     * @param productId Produkt-ID
     * @param from      Startdatum (ISO-8601, default: -90 Tage)
     * @param to        Enddatum (ISO-8601, default: jetzt)
     * @param limit     Max. Einträge (default: 100)
     * @param currentUser null bei Gästen (anonymer Zugriff)
     */
    @GetMapping
    public ResponseEntity<?> getPriceHistory(
            @PathVariable Long productId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "100") int limit,
            @AuthenticationPrincipal User currentUser) {

        Instant resolvedTo = to != null ? to : Instant.now();
        Instant resolvedFrom = from != null ? from : resolvedTo.minus(90, ChronoUnit.DAYS);

        try {
            boolean isAdmin = currentUser != null && currentUser.hasRole(UserRole.ADMIN);

            if (isAdmin) {
                List<PriceHistoryAdminDto> adminHistory =
                        historyService.getAdminHistory(productId, resolvedFrom, resolvedTo, limit);
                return ResponseEntity.ok(adminHistory);
            } else {
                List<PriceHistoryPublicDto> publicHistory =
                        historyService.getPublicHistory(productId, resolvedFrom, resolvedTo, limit);
                return ResponseEntity.ok(publicHistory);
            }
        } catch (EntityNotFoundException e) {
            throw new ResponseStatusException(NOT_FOUND, e.getMessage());
        }
    }
}
