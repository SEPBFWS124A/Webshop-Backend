package de.fhdw.webshop.admin.navigation.dto;

import de.fhdw.webshop.user.UserRole;

import java.util.Set;

public record AdminNavigationItemResponse(
        String id,
        String label,
        String path,
        String icon,
        Set<UserRole> allowedRoles
) {}
