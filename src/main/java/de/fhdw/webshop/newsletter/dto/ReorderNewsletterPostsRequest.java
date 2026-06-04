package de.fhdw.webshop.newsletter.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ReorderNewsletterPostsRequest(@NotNull List<Long> orderedIds) {}
