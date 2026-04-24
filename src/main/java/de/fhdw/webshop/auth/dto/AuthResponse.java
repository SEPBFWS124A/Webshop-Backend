package de.fhdw.webshop.auth.dto;

import de.fhdw.webshop.user.UserRole;
import de.fhdw.webshop.user.UserType;

import java.util.Set;

/**
 * Returned after a successful login or registration.
 * The frontend stores the token and sends it as "Authorization: Bearer {token}" on every request.
 */
public record AuthResponse(
        String token,
        Long userId,
        String username,
        String email,
        Set<UserRole> roles,
        UserType userType,
        String customerNumber
) {}
