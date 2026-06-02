package de.fhdw.webshop.user.dto;

import jakarta.validation.constraints.NotNull;

public record CartReminderSettingsRequest(
        @NotNull Boolean enabled
) {}
