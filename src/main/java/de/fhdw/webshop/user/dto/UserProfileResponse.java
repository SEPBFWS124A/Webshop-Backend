package de.fhdw.webshop.user.dto;

import de.fhdw.webshop.user.UserRole;
import de.fhdw.webshop.user.UserType;

public record UserProfileResponse(
        Long id,
        String username,
        String email,
        UserRole role,
        UserType userType,
        String customerNumber
) {}
