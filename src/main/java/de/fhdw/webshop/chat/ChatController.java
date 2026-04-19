package de.fhdw.webshop.chat;

import de.fhdw.webshop.chat.dto.ChatMessageRequest;
import de.fhdw.webshop.chat.dto.ChatMessageResponse;
import de.fhdw.webshop.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * Public endpoint — works for both anonymous and authenticated users.
     * Authenticated users receive personal context (cart, orders, profile) in the bot's response.
     */
    @PostMapping("/message")
    public ResponseEntity<ChatMessageResponse> sendMessage(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody ChatMessageRequest request) {
        return ResponseEntity.ok(chatService.processMessage(currentUser, request));
    }
}
