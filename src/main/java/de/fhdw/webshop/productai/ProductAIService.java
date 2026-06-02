package de.fhdw.webshop.productai;

import de.fhdw.webshop.chat.OllamaClient;
import de.fhdw.webshop.productai.dto.GenerateContentRequest;
import de.fhdw.webshop.productai.dto.GenerateContentResponse;
import de.fhdw.webshop.productai.dto.SuggestCategoryRequest;
import de.fhdw.webshop.productai.dto.SuggestCategoryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductAIService {

    private static final Pattern TITLE_PATTERN = Pattern.compile("(?i)TITEL:\\s*(.+)");
    private static final Pattern DESCRIPTION_PATTERN = Pattern.compile("(?i)BESCHREIBUNG:\\s*([\\s\\S]+?)(?:KATEGORIE:|$)");
    private static final Pattern CATEGORY_PATTERN = Pattern.compile("(?i)KATEGORIE:\\s*(.+)");
    private static final Pattern TAGS_PATTERN = Pattern.compile("(?i)TAGS:\\s*(.+)");

    private final OllamaClient ollamaClient;

    public GenerateContentResponse generateContent(GenerateContentRequest request) {
        String systemPrompt = """
                Du bist ein erfahrener E-Commerce-Texter, der SEO-optimierte Produkttexte für einen deutschen Webshop erstellt.
                Antworte ausschließlich im folgenden Format, ohne zusätzliche Erklärungen:
                TITEL: <prägnanter, SEO-optimierter Produkttitel, max. 80 Zeichen>
                BESCHREIBUNG: <ansprechender Verkaufstext, 2-4 Sätze, SEO-optimiert, auf Deutsch>
                """;

        String userPrompt = buildContentUserPrompt(request);
        String rawResponse = ollamaClient.chat(systemPrompt, List.of(
                new de.fhdw.webshop.chat.dto.ConversationEntry("user", userPrompt)
        ));

        return parseContentResponse(rawResponse, request.keywords());
    }

    public SuggestCategoryResponse suggestCategory(SuggestCategoryRequest request) {
        String systemPrompt = """
                Du bist ein E-Commerce-Kategorie-Experte für einen deutschen Webshop.
                Analysiere den Produkttext und antworte ausschließlich im folgenden Format:
                KATEGORIE: <eine der folgenden Kategorien: Electronics, Furniture, Stationery, Clothing, Sports, Kitchen, Books, Toys, Beauty, Other>
                TAGS: <3-5 kommagetrennte Schlagwörter auf Deutsch, z.B. "ergonomisch, büro, modern">
                """;

        String userPrompt = "Analysiere diesen Produkttext und schlage Kategorie und Tags vor:\n\n" + request.text();
        String rawResponse = ollamaClient.chat(systemPrompt, List.of(
                new de.fhdw.webshop.chat.dto.ConversationEntry("user", userPrompt)
        ));

        return parseCategoryResponse(rawResponse);
    }

    private String buildContentUserPrompt(GenerateContentRequest request) {
        StringBuilder prompt = new StringBuilder("Erstelle einen SEO-optimierten Produkttitel und Verkaufstext für folgende Stichpunkte:\n\n");
        prompt.append(request.keywords());
        if (request.category() != null && !request.category().isBlank()) {
            prompt.append("\n\nKategorie: ").append(request.category());
        }
        return prompt.toString();
    }

    private GenerateContentResponse parseContentResponse(String rawResponse, String fallbackKeywords) {
        String title = extractGroup(TITLE_PATTERN, rawResponse);
        String description = extractGroup(DESCRIPTION_PATTERN, rawResponse);

        if (title.isBlank()) {
            title = fallbackKeywords.lines().findFirst().orElse("Neues Produkt").trim();
        }
        if (description.isBlank()) {
            description = rawResponse.trim();
        }

        return new GenerateContentResponse(title.trim(), description.trim());
    }

    private SuggestCategoryResponse parseCategoryResponse(String rawResponse) {
        String category = extractGroup(CATEGORY_PATTERN, rawResponse);
        String tagsRaw = extractGroup(TAGS_PATTERN, rawResponse);

        if (category.isBlank()) {
            category = "Other";
        }

        List<String> tags = tagsRaw.isBlank()
                ? List.of()
                : Arrays.stream(tagsRaw.split(","))
                        .map(String::trim)
                        .filter(t -> !t.isBlank())
                        .toList();

        return new SuggestCategoryResponse(category.trim(), tags);
    }

    private String extractGroup(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : "";
    }
}
