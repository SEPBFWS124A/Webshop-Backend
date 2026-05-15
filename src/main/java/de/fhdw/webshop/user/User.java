package de.fhdw.webshop.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Persistent user account. Implements Spring Security's UserDetails so it can be
 * used directly by the authentication infrastructure without a separate adapter.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role", length = 50)
    @Enumerated(EnumType.STRING)
    private Set<UserRole> roles = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "user_type", nullable = false, columnDefinition = "user_type")
    private UserType userType = UserType.PRIVATE;

    @Column(name = "customer_number", unique = true, length = 20)
    private String customerNumber;

    @Column(name = "employee_number", unique = true, length = 20)
    private String employeeNumber;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "wishlist_state", columnDefinition = "TEXT")
    private String wishlistState;

    // ── Loyalty: Login-Streak ──────────────────────────────────────────────────

    @Column(name = "last_login_date")
    private LocalDate lastLoginDate;

    @Column(name = "current_login_streak", nullable = false)
    private int currentLoginStreak = 0;

    @Column(name = "agb_accepted_at")
    private Instant agbAcceptedAt;

    // ── Convenience helpers ────────────────────────────────────────────────────

    public boolean hasRole(UserRole role) {
        return roles != null && roles.contains(role);
    }

    /** Returns the highest-privilege role for display/token purposes. */
    public UserRole getPrimaryRole() {
        if (roles == null || roles.isEmpty()) return UserRole.CUSTOMER;
        for (UserRole r : List.of(UserRole.ADMIN, UserRole.SALES_EMPLOYEE,
                UserRole.WAREHOUSE_EMPLOYEE, UserRole.EMPLOYEE, UserRole.CUSTOMER)) {
            if (roles.contains(r)) return r;
        }
        return roles.iterator().next();
    }

    // ── UserDetails interface ──────────────────────────────────────────────────

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (roles == null || roles.isEmpty()) {
            return List.of();
        }
        return roles.stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r.name()))
                .toList();
    }

    /** Spring Security expects the credential via getPassword(), not getPasswordHash(). */
    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return active;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
