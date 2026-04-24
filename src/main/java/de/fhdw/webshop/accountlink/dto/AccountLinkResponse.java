package de.fhdw.webshop.accountlink.dto;

import de.fhdw.webshop.user.dto.UserProfileResponse;
import java.time.Instant;

public record AccountLinkResponse(
        Long id,
        UserProfileResponse linkedUser,
        Instant createdAt
) {}
