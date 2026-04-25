package de.fhdw.webshop.user.dto;

import de.fhdw.webshop.user.UserRole;
import de.fhdw.webshop.user.UserType;

import java.util.Set;

public record UserProfileResponse(
        Long id,
        String username,
        String email,
        Set<UserRole> roles,
        UserType userType,
        String customerNumber,
        boolean active
) {}
