package de.fhdw.webshop.chat.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record ChatMessageRequest(
        @NotBlank String message,
        List<ConversationEntry> history
) {}
