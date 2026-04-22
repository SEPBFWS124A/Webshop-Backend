package de.fhdw.webshop.chat;

import de.fhdw.webshop.chat.dto.ConversationEntry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class OllamaClient {

    private final RestClient restClient;
    private final String modelName;
    private final MeterRegistry meterRegistry;

    public OllamaClient(
            @Value("${ollama.base-url}") String ollamaBaseUrl,
            @Value("${ollama.model}") String modelName,
            MeterRegistry meterRegistry) {
        this.restClient = RestClient.builder()
                .baseUrl(ollamaBaseUrl)
                .build();
        this.modelName = modelName;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Sends a chat request to the Ollama API and returns the assistant's reply.
     * The systemPrompt is always prepended as the first message with role "system".
     */
    public String chat(String systemPrompt, List<ConversationEntry> conversationHistory) {
        List<Map<String, String>> messages = buildMessageList(systemPrompt, conversationHistory);

        Map<String, Object> requestBody = Map.of(
                "model", modelName,
                "messages", messages,
                "stream", false
        );

        Timer.Sample timerSample = Timer.start(meterRegistry);
        String outcome = "success";
        try {
            OllamaResponse response = restClient.post()
                    .uri("/api/chat")
                    .body(requestBody)
                    .retrieve()
                    .body(OllamaResponse.class);

            if (response == null || response.message() == null) {
                return "Entschuldigung, ich konnte keine Antwort generieren. Bitte versuche es erneut.";
            }
            return response.message().content();
        } catch (Exception exception) {
            outcome = "error";
            log.error("Ollama request failed: {}", exception.getMessage());
            return "Entschuldigung, der KI-Assistent ist gerade nicht erreichbar. Bitte versuche es später erneut.";
        } finally {
            timerSample.stop(Timer.builder("ollama.chat.duration")
                    .tag("model", modelName)
                    .tag("outcome", outcome)
                    .description("Duration of Ollama chat completion requests")
                    .publishPercentileHistogram(true)
                    .register(meterRegistry));
        }
    }

    private List<Map<String, String>> buildMessageList(String systemPrompt, List<ConversationEntry> history) {
        List<Map<String, String>> messages = new java.util.ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));

        if (history != null) {
            for (ConversationEntry entry : history) {
                messages.add(Map.of("role", entry.role(), "content", entry.content()));
            }
        }
        return messages;
    }

    private record OllamaResponse(OllamaMessage message) {}
    private record OllamaMessage(String role, String content) {}
}
