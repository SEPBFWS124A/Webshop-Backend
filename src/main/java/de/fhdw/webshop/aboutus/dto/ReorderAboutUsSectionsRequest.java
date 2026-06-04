package de.fhdw.webshop.aboutus.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ReorderAboutUsSectionsRequest(@NotNull List<Long> orderedIds) {}
