package de.fhdw.webshop.admin.dto;

import de.fhdw.webshop.user.UserRole;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public record UpdateUserRolesRequest(
        @NotEmpty(message = "Mindestens eine Rolle muss zugewiesen sein")
        Set<UserRole> roles
) {}
