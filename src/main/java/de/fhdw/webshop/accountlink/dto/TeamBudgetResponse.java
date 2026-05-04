package de.fhdw.webshop.accountlink.dto;

import de.fhdw.webshop.user.dto.UserProfileResponse;
import java.math.BigDecimal;
import java.time.Instant;

public record TeamBudgetResponse(
        Long linkId,
        UserProfileResponse employee,
        BigDecimal maxOrderValueLimit,
        boolean unlimited,
        Instant linkedAt
) {}
