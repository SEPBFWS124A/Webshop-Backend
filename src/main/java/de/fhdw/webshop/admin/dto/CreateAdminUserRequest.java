package de.fhdw.webshop.admin.dto;

import de.fhdw.webshop.user.UserRole;
import de.fhdw.webshop.user.UserType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateAdminUserRequest(
        @NotBlank @Size(min = 3, max = 100) String username,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8) String password,
        @NotNull UserType userType,
        @NotNull UserRole role
) {}
