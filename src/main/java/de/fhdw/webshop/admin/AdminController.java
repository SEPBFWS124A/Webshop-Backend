package de.fhdw.webshop.admin;

import de.fhdw.webshop.auth.JwtTokenProvider;
import de.fhdw.webshop.auth.dto.AuthResponse;
import de.fhdw.webshop.alerting.BusinessEmailService;
import de.fhdw.webshop.user.User;
import de.fhdw.webshop.user.UserRepository;
import de.fhdw.webshop.user.dto.UserProfileResponse;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditLogService auditLogService;
    private final JwtTokenProvider jwtTokenProvider;
    private final BusinessEmailService businessEmailService;

    /** US #59, #60 — List all users, optionally filtered by search term. */
    @GetMapping("/users")
    public ResponseEntity<List<UserProfileResponse>> listAllUsers(
            @RequestParam(required = false) String search) {
        List<UserProfileResponse> users = userRepository.findAllUsers(search == null ? "" : search).stream()
                .map(user -> new UserProfileResponse(
                        user.getId(), user.getUsername(), user.getEmail(),
                        user.getRole(), user.getUserType(), user.getCustomerNumber()))
                .toList();
        return ResponseEntity.ok(users);
    }

    /** US #61 — Deactivate any user account. */
    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deactivateUser(@PathVariable Long id,
                                               @AuthenticationPrincipal User adminUser) {
        User targetUser = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + id));
        targetUser.setActive(false);
        userRepository.save(targetUser);
        auditLogService.record(adminUser, "DEACTIVATE_USER", "User", id,
                AuditInitiator.ADMIN, "Admin deactivated user: " + targetUser.getUsername());
        return ResponseEntity.noContent().build();
    }

    /**
     * US #19 — Generate a JWT that is scoped to the target user's identity.
     * The admin can then use this token to act on behalf of the customer.
     */
    @PostMapping("/impersonate/{userId}")
    public ResponseEntity<AuthResponse> impersonateUser(@PathVariable Long userId,
                                                        @AuthenticationPrincipal User adminUser) {
        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
        String impersonationToken = jwtTokenProvider.generateToken(targetUser);
        auditLogService.record(adminUser, "IMPERSONATE_USER", "User", userId,
                AuditInitiator.ADMIN, "Admin impersonated user: " + targetUser.getUsername());
        return ResponseEntity.ok(new AuthResponse(
                impersonationToken,
                targetUser.getId(),
                targetUser.getUsername(),
                targetUser.getEmail(),
                targetUser.getRole(),
                targetUser.getUserType(),
                targetUser.getCustomerNumber()
        ));
    }

    /** US #58 — View full audit log. */
    @GetMapping("/audit-log")
    public ResponseEntity<List<AuditLog>> getAuditLog() {
        return ResponseEntity.ok(auditLogRepository.findAllByOrderByTimestampDesc());
    }

    /** Sends a test alert email to verify the alerting configuration. */
    @PostMapping("/alerts/test")
    public ResponseEntity<Void> sendTestAlert() {
        businessEmailService.sendTestAlert(
                "Test Alert — Webshop Monitoring",
                "Dies ist eine Test-Benachrichtigung.\n\n"
                + "Die Alert-Konfiguration funktioniert korrekt.\n"
                + "Empfänger, SMTP-Host und Zugangsdaten sind gültig.");
        return ResponseEntity.noContent().build();
    }
}
