package de.fhdw.webshop.admin.dto;

import de.fhdw.webshop.user.UserRole;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record UpdateUserRolesRequest(
        @NotEmpty(message = "Eine Rolle muss zugewiesen sein")
        @Size(min = 1, max = 1, message = "Es darf genau eine Rolle zugewiesen sein")
        Set<UserRole> roles
) {}
