package de.fhdw.webshop.alerting.dto;

import de.fhdw.webshop.alerting.RecipientStrategy;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdateAlertEventConfigRequest(
        boolean enabled,
        @NotNull RecipientStrategy strategy,
        List<Long> recipientIds
) {}
