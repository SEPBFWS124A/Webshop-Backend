package de.fhdw.webshop.admin;

import de.fhdw.webshop.accountlink.AccountLinkService;
import de.fhdw.webshop.accountlink.dto.AccountLinkResponse;
import de.fhdw.webshop.accountlink.dto.CreateAccountLinksRequest;
import de.fhdw.webshop.admin.dto.CreateAdminUserRequest;
import de.fhdw.webshop.admin.dto.UpdateUserRolesRequest;
import de.fhdw.webshop.alerting.BusinessEmailService;
import de.fhdw.webshop.auth.JwtTokenProvider;
import de.fhdw.webshop.auth.dto.AuthResponse;
import de.fhdw.webshop.user.User;
import de.fhdw.webshop.user.UserRepository;
import de.fhdw.webshop.user.UserRole;
import de.fhdw.webshop.user.UserType;
import de.fhdw.webshop.user.dto.UserProfileResponse;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

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
    private final AccountLinkService accountLinkService;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    /** US #59, #60 — List all users, optionally filtered by search term. */
    @GetMapping("/users")
    public ResponseEntity<List<UserProfileResponse>> listAllUsers(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "false") boolean activeOnly) {
        String searchTerm = search == null ? "" : search.trim();
        Long searchId = parseIdSearch(searchTerm);
        List<User> matchingUsers = searchId == null
                ? userRepository.findAllUsers(searchTerm, activeOnly)
                : userRepository.findById(searchId)
                        .filter(user -> !activeOnly || user.isActive())
                        .stream()
                        .toList();

        List<UserProfileResponse> users = matchingUsers.stream()
                .map(user -> new UserProfileResponse(
                        user.getId(), user.getUsername(), user.getEmail(),
                        user.getRoles(), user.getUserType(), user.getCustomerNumber(),
                        user.isActive()))
                .toList();
        return ResponseEntity.ok(users);
    }

    private Long parseIdSearch(String searchTerm) {
        if (searchTerm == null || !searchTerm.matches("\\d+")) {
            return null;
        }

        try {
            return Long.parseLong(searchTerm);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @PostMapping("/users")
    public ResponseEntity<UserProfileResponse> createUser(
            @Valid @RequestBody CreateAdminUserRequest request,
            @AuthenticationPrincipal User adminUser) {
        if (userRepository.existsByUsername(request.username())) {
            throw new IllegalArgumentException("Username already taken: " + request.username());
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email already registered: " + request.email());
        }

        UserRole effectiveRole = determineRoleForUserType(request.userType(), request.role());

        User newUser = new User();
        newUser.setUsername(request.username());
        newUser.setEmail(request.email());
        newUser.setPasswordHash(passwordEncoder.encode(request.password()));
        newUser.setUserType(request.userType());
        newUser.getRoles().add(effectiveRole);

        if (effectiveRole == UserRole.CUSTOMER) {
            Long nextSequenceValue = jdbcTemplate.queryForObject(
                    "SELECT nextval('customer_number_sequence')", Long.class);
            newUser.setCustomerNumber(String.valueOf(nextSequenceValue));
        }

        User savedUser = userRepository.save(newUser);
        auditLogService.record(adminUser, "CREATE_USER", "User", savedUser.getId(),
                AuditInitiator.ADMIN, "Admin created user: " + savedUser.getUsername());

        return ResponseEntity.status(HttpStatus.CREATED).body(toProfileResponse(savedUser));
    }

    private UserRole determineRoleForUserType(UserType userType, UserRole requestedRole) {
        if (userType == UserType.BUSINESS || userType == UserType.PRIVATE) {
            return UserRole.CUSTOMER;
        }
        if (requestedRole == UserRole.CUSTOMER) {
            throw new IllegalArgumentException("Interne Benutzer duerfen nicht die Rolle Kunde erhalten.");
        }
        return requestedRole;
    }

    /** Issue #134 — US-01: Get current roles of a user. */
    @GetMapping("/users/{id}/roles")
    public ResponseEntity<Set<UserRole>> getUserRoles(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + id));
        return ResponseEntity.ok(user.getRoles());
    }

    /** Issue #134 — US-01: Assign type and role to a user.
     *  Lockout protection: an admin cannot remove their own ADMIN role. */
    @PutMapping("/users/{id}/roles")
    public ResponseEntity<UserProfileResponse> updateUserRoles(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRolesRequest request,
            @AuthenticationPrincipal User adminUser) {
        User targetUser = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + id));
        UserRole effectiveRole = determineRoleForUserType(request.userType(), request.roles().iterator().next());

        // Lockout-Schutz: Admin darf sich nicht selbst die ADMIN-Rolle entziehen
        if (adminUser.getId().equals(targetUser.getId())
                && targetUser.hasRole(UserRole.ADMIN)
                && effectiveRole != UserRole.ADMIN) {
            throw new IllegalArgumentException(
                    "Administratoren können sich nicht selbst die Admin-Rolle entziehen.");
        }

        Set<UserRole> previousRoles = Set.copyOf(targetUser.getRoles());
        UserType previousType = targetUser.getUserType();
        Set<UserRole> newRoles = Set.of(effectiveRole);

        targetUser.setUserType(request.userType());
        targetUser.getRoles().clear();
        targetUser.getRoles().addAll(newRoles);

        if (effectiveRole == UserRole.CUSTOMER && targetUser.getCustomerNumber() == null) {
            Long nextSequenceValue = jdbcTemplate.queryForObject(
                    "SELECT nextval('customer_number_sequence')", Long.class);
            targetUser.setCustomerNumber(String.valueOf(nextSequenceValue));
        } else if (effectiveRole != UserRole.CUSTOMER) {
            targetUser.setCustomerNumber(null);
        }

        userRepository.save(targetUser);

        String details = buildRoleChangeDetails(previousRoles, newRoles, targetUser.getUsername())
                + String.format(" | Type: %s -> %s", previousType, targetUser.getUserType());
        auditLogService.record(adminUser, "UPDATE_USER_ROLES", "User", id,
                AuditInitiator.ADMIN, details);

        return ResponseEntity.ok(toProfileResponse(targetUser));
    }

    private String buildRoleChangeDetails(Set<UserRole> previous, Set<UserRole> next, String targetUsername) {
        Set<UserRole> added = new java.util.HashSet<>(next);
        added.removeAll(previous);
        Set<UserRole> removed = new java.util.HashSet<>(previous);
        removed.removeAll(next);
        return String.format("User: %s | Added: %s | Removed: %s", targetUsername, added, removed);
    }

    private UserProfileResponse toProfileResponse(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRoles(),
                user.getUserType(),
                user.getCustomerNumber(),
                user.isActive());
    }

    @PatchMapping("/users/{id}/deactivate")
    public ResponseEntity<UserProfileResponse> deactivateUserAccount(@PathVariable Long id,
                                                                     @AuthenticationPrincipal User adminUser) {
        User targetUser = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + id));
        if (adminUser.getId().equals(targetUser.getId())) {
            throw new IllegalArgumentException("Administratoren koennen ihr eigenes Konto nicht deaktivieren.");
        }

        if (targetUser.isActive()) {
            targetUser.setActive(false);
            userRepository.save(targetUser);
            auditLogService.record(adminUser, "DEACTIVATE_USER_ACCOUNT", "User", id,
                    AuditInitiator.ADMIN, "Admin deactivated user account: " + targetUser.getUsername());
        }

        return ResponseEntity.ok(toProfileResponse(targetUser));
    }

    @PatchMapping("/users/{id}/activate")
    public ResponseEntity<UserProfileResponse> activateUserAccount(@PathVariable Long id,
                                                                   @AuthenticationPrincipal User adminUser) {
        User targetUser = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + id));
        if (targetUser.getUsername().startsWith("deleted_")
                && targetUser.getEmail().endsWith("@deleted.invalid")) {
            throw new IllegalArgumentException("Geloeschte Benutzerkonten koennen nicht reaktiviert werden.");
        }

        if (!targetUser.isActive()) {
            targetUser.setActive(true);
            userRepository.save(targetUser);
            auditLogService.record(adminUser, "ACTIVATE_USER_ACCOUNT", "User", id,
                    AuditInitiator.ADMIN, "Admin activated user account: " + targetUser.getUsername());
        }

        return ResponseEntity.ok(toProfileResponse(targetUser));
    }

    /** US #61 — Deactivate any user account. */
    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deactivateUser(@PathVariable Long id,
                                               @AuthenticationPrincipal User adminUser) {
        User targetUser = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + id));
        if (adminUser.getId().equals(targetUser.getId())) {
            throw new IllegalArgumentException("Administratoren koennen ihr eigenes Konto nicht loeschen.");
        }
        String deletedUsername = targetUser.getUsername();
        accountLinkService.removeLinksForUser(id);
        targetUser.setActive(false);
        targetUser.setUsername("deleted_" + targetUser.getId());
        targetUser.setEmail("deleted_" + targetUser.getId() + "@deleted.invalid");
        userRepository.save(targetUser);
        auditLogService.record(adminUser, "DEACTIVATE_USER", "User", id,
                AuditInitiator.ADMIN, "Admin deactivated user: " + deletedUsername);
        return ResponseEntity.noContent().build();
    }

    /** Issue #136 - List linked accounts for a user profile. */
    @GetMapping("/users/{id}/links")
    public ResponseEntity<List<AccountLinkResponse>> listAccountLinks(@PathVariable Long id) {
        return ResponseEntity.ok(accountLinkService.listLinks(id));
    }

    /** Issue #136 - Link one or more accounts to a user profile. */
    @PostMapping("/users/{id}/links")
    public ResponseEntity<List<AccountLinkResponse>> createAccountLinks(
            @PathVariable Long id,
            @Valid @RequestBody CreateAccountLinksRequest request,
            @AuthenticationPrincipal User adminUser) {
        return ResponseEntity.ok(accountLinkService.createLinks(id, request.linkedUserIds(), adminUser));
    }

    /** Issue #136 - Remove an account link from a user profile. */
    @DeleteMapping("/users/{id}/links/{linkedUserId}")
    public ResponseEntity<Void> deleteAccountLink(
            @PathVariable Long id,
            @PathVariable Long linkedUserId,
            @AuthenticationPrincipal User adminUser) {
        accountLinkService.deleteLink(id, linkedUserId, adminUser);
        return ResponseEntity.noContent().build();
    }

    /**
     * US #19 — Generate a JWT that is scoped to the target user's identity.
     * The admin can then use this token to act on behalf of the customer.
     */
    @PostMapping({"/impersonate/{userId}", "/users/{userId}/impersonate"})
    public ResponseEntity<AuthResponse> impersonateUser(@PathVariable Long userId,
                                                        @AuthenticationPrincipal User adminUser) {
        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));

        if (!targetUser.isActive()) {
            throw new IllegalArgumentException("Deaktivierte Benutzerkonten koennen nicht impersoniert werden.");
        }
        if (targetUser.hasRole(UserRole.ADMIN)) {
            throw new IllegalArgumentException("Administratoren koennen keine Administrator-Identitaet annehmen.");
        }

        String impersonationToken = jwtTokenProvider.generateImpersonationToken(targetUser, adminUser);
        auditLogService.record(adminUser, "IMPERSONATE_USER", "User", userId,
                AuditInitiator.ADMIN,
                String.format("Admin %s (%d) impersonated user %s (%d)",
                        adminUser.getUsername(), adminUser.getId(), targetUser.getUsername(), targetUser.getId()));
        return ResponseEntity.ok(new AuthResponse(
                impersonationToken,
                targetUser.getId(),
                targetUser.getUsername(),
                targetUser.getEmail(),
                targetUser.getRoles(),
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
