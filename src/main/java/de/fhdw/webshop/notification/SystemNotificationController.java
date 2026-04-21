package de.fhdw.webshop.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    @PreAuthorize("hasAnyRole('SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<List<SystemNotificationResponse>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    /** Anzahl ungelesener Benachrichtigungen (für Glocken-Badge). */
    @GetMapping("/unread-count")
    @PreAuthorize("hasAnyRole('SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<Map<String, Long>> getUnreadCount() {
        return ResponseEntity.ok(Map.of("count", service.getUnreadCount()));
    }

    /** Einzelne Benachrichtigung als gelesen markieren. */
    @PutMapping("/{id}/read")
    @PreAuthorize("hasAnyRole('SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<SystemNotificationResponse> markAsRead(@PathVariable Long id) {
        return ResponseEntity.ok(service.markAsRead(id));
    }

    /** Alle Benachrichtigungen als gelesen markieren. */
    @PutMapping("/read-all")
    @PreAuthorize("hasAnyRole('SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<Void> markAllAsRead() {
        service.markAllAsRead();
        return ResponseEntity.noContent().build();
    }
}
