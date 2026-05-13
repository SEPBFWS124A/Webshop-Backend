package de.fhdw.webshop.notification;

import de.fhdw.webshop.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * US #90 — Systembenachrichtigungen für Vertriebsmitarbeiter:
 * Abruf der Benachrichtigungsliste sowie Markierung als gelesen.
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class SystemNotificationController {

    private final SystemNotificationService service;

    /** Alle Benachrichtigungen (neueste zuerst). */
    @GetMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<List<SystemNotificationResponse>> getAll(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(service.getAll(currentUser));
    }

    /** Anzahl ungelesener Benachrichtigungen (für Glocken-Badge). */
    @GetMapping("/unread-count")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<Map<String, Long>> getUnreadCount(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(Map.of("count", service.getUnreadCount(currentUser)));
    }

    /** Einzelne Benachrichtigung als gelesen markieren. */
    @PutMapping("/{id}/read")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<SystemNotificationResponse> markAsRead(@PathVariable Long id,
                                                                 @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(service.markAsRead(id, currentUser));
    }

    /** Alle Benachrichtigungen als gelesen markieren. */
    @PutMapping("/read-all")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<Void> markAllAsRead(@AuthenticationPrincipal User currentUser) {
        service.markAllAsRead(currentUser);
        return ResponseEntity.noContent().build();
    }
}
