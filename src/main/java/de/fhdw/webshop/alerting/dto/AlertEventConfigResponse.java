package de.fhdw.webshop.alerting.dto;

import de.fhdw.webshop.alerting.RecipientStrategy;

import java.util.List;

public record AlertEventConfigResponse(
        String eventType,
        String displayName,
        boolean enabled,
        RecipientStrategy strategy,
        List<KnownEmailAddressResponse> recipients
) {}
