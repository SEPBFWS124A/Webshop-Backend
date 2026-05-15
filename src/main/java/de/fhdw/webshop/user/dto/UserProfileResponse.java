package de.fhdw.webshop.user.dto;

import de.fhdw.webshop.user.UserRole;
import de.fhdw.webshop.user.UserType;

import java.time.Instant;
import java.util.Set;

public record UserProfileResponse(
        Long id,
        String username,
        String email,
        Set<UserRole> roles,
        boolean active,
        String employeeNumber,
        UserType userType,
        String customerNumber,
        Instant agbAcceptedAt
) {}
