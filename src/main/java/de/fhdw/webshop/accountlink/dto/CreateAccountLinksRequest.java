package de.fhdw.webshop.accountlink.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CreateAccountLinksRequest(
        @NotEmpty List<@NotNull Long> linkedUserIds
) {}
