package de.fhdw.webshop.recommendation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.fhdw.webshop.cart.CartService;
import de.fhdw.webshop.cart.dto.CartItemResponse;
import de.fhdw.webshop.cart.dto.CartResponse;
import de.fhdw.webshop.chat.OllamaClient;
import de.fhdw.webshop.chat.dto.ConversationEntry;
import de.fhdw.webshop.order.OrderService;
import de.fhdw.webshop.order.dto.OrderResponse;
import de.fhdw.webshop.product.ProductService;
import de.fhdw.webshop.product.dto.ProductResponse;
import de.fhdw.webshop.recommendation.dto.ProductRecommendationItemResponse;
import de.fhdw.webshop.recommendation.dto.ProductRecommendationListResponse;
import de.fhdw.webshop.user.User;
import de.fhdw.webshop.user.UserType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductRecommendationService {

    private static final int DEFAULT_LIMIT = 4;
    private static final int MAX_LIMIT = 8;

    private final ProductService productService;
    private final CartService cartService;
    private final OrderService orderService;
    private final OllamaClient ollamaClient;
    private final ObjectMapper objectMapper;

    public ProductRecommendationListResponse getRecommendationsForProduct(Long productId, User currentUser, Integer limit) {
        int resolvedLimit = resolveLimit(limit);
        ProductResponse currentProduct = productService.getProduct(productId);
        List<ProductResponse> candidates = listCandidateProducts(product -> !product.id().equals(productId));
        boolean personalized = currentUser != null;

        if (candidates.isEmpty()) {
            return new ProductRecommendationListResponse(List.of(), personalized, true);
        }

        List<ProductRecommendationItemResponse> aiRecommendations = requestAiRecommendations(
                buildProductSystemPrompt(currentProduct, candidates, currentUser, resolvedLimit),
                "Erzeuge passende Produktempfehlungen fuer die aktuelle Produktdetailseite.",
                candidates,
                resolvedLimit);

        if (!aiRecommendations.isEmpty()) {
            return new ProductRecommendationListResponse(aiRecommendations, personalized, false);
        }

        return new ProductRecommendationListResponse(
                buildProductFallbackRecommendations(currentProduct, candidates, resolvedLimit),
                personalized,
                true);
    }

    public ProductRecommendationListResponse getRecommendationsForCart(User currentUser, Integer limit) {
        int resolvedLimit = resolveLimit(limit);
        CartResponse cart = cartService.getCart(currentUser.getId());
        List<CartItemResponse> cartItems = cart.items();
        if (cartItems.isEmpty()) {
            return new ProductRecommendationListResponse(List.of(), true, true);
        }

        Set<Long> cartProductIds = cartItems.stream()
                .map(CartItemResponse::productId)
                .collect(Collectors.toSet());
        List<ProductResponse> candidates = listCandidateProducts(product -> !cartProductIds.contains(product.id()));

        if (candidates.isEmpty()) {
            return new ProductRecommendationListResponse(List.of(), true, true);
        }

        List<ProductRecommendationItemResponse> aiRecommendations = requestAiRecommendations(
                buildCartSystemPrompt(cart, candidates, currentUser, resolvedLimit),
                "Erzeuge passende Zusatzprodukte fuer den aktuellen Warenkorb.",
                candidates,
                resolvedLimit);

        if (!aiRecommendations.isEmpty()) {
            return new ProductRecommendationListResponse(aiRecommendations, true, false);
        }

        return new ProductRecommendationListResponse(
                buildCartFallbackRecommendations(cart, candidates, resolvedLimit),
                true,
                true);
    }

    private List<ProductResponse> listCandidateProducts(Predicate<ProductResponse> filter) {
        return productService.listProducts(true, null, null).stream()
                .filter(filter)
                .toList();
    }

    private List<ProductRecommendationItemResponse> requestAiRecommendations(
            String systemPrompt,
            String userMessage,
            List<ProductResponse> candidates,
            int limit) {
        Map<Long, ProductResponse> candidatesById = candidates.stream()
                .collect(Collectors.toMap(ProductResponse::id, product -> product, (left, right) -> left, LinkedHashMap::new));

        String rawResponse = ollamaClient.chat(systemPrompt, List.of(new ConversationEntry("user", userMessage)));
        List<ParsedRecommendation> parsedRecommendations = parseRecommendations(rawResponse);

        if (parsedRecommendations.isEmpty()) {
            return List.of();
        }

        Map<Long, ProductRecommendationItemResponse> resolved = new LinkedHashMap<>();
        for (ParsedRecommendation parsedRecommendation : parsedRecommendations) {
            ProductResponse product = candidatesById.get(parsedRecommendation.productId());
            if (product == null || resolved.containsKey(product.id())) {
                continue;
            }
            resolved.put(product.id(), new ProductRecommendationItemResponse(
                    product,
                    sanitizeReason(parsedRecommendation.reason()),
                    "ai"));
            if (resolved.size() >= limit) {
                break;
            }
        }

        return List.copyOf(resolved.values());
    }

    private List<ParsedRecommendation> parseRecommendations(String rawResponse) {
        try {
            String normalized = normalizeJsonPayload(rawResponse);
            if (normalized.isBlank()) {
                return List.of();
            }

            JsonNode root = objectMapper.readTree(normalized);
            JsonNode recommendationsNode = root.isArray() ? root : root.path("recommendations");
            if (!recommendationsNode.isArray()) {
                return List.of();
            }

            List<ParsedRecommendation> parsed = new ArrayList<>();
            for (JsonNode node : recommendationsNode) {
                long productId = node.path("productId").asLong(0);
                String reason = node.path("reason").asText("");
                if (productId > 0) {
                    parsed.add(new ParsedRecommendation(productId, reason));
                }
            }
            return parsed;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String normalizeJsonPayload(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return "";
        }

        String cleaned = rawResponse
                .replace("```json", "")
                .replace("```", "")
                .trim();
        int objectStart = cleaned.indexOf('{');
        int arrayStart = cleaned.indexOf('[');

        if (objectStart < 0 && arrayStart < 0) {
            return "";
        }

        if (arrayStart >= 0 && (objectStart < 0 || arrayStart < objectStart)) {
            int arrayEnd = cleaned.lastIndexOf(']');
            return arrayEnd > arrayStart ? cleaned.substring(arrayStart, arrayEnd + 1) : "";
        }

        int objectEnd = cleaned.lastIndexOf('}');
        return objectEnd > objectStart ? cleaned.substring(objectStart, objectEnd + 1) : "";
    }

    private List<ProductRecommendationItemResponse> buildProductFallbackRecommendations(
            ProductResponse currentProduct,
            List<ProductResponse> candidates,
            int limit) {
        return candidates.stream()
                .sorted(Comparator
                        .comparing((ProductResponse candidate) -> !sameCategory(currentProduct.category(), candidate.category()))
                        .thenComparing(candidate -> !candidate.promoted())
                        .thenComparing(ProductResponse::name))
                .limit(limit)
                .map(product -> new ProductRecommendationItemResponse(
                        product,
                        sameCategory(currentProduct.category(), product.category())
                                ? "Passt zur gleichen Kategorie wie dieser Artikel."
                                : "Beliebte Alternative aus dem restlichen Sortiment.",
                        "fallback"))
                .toList();
    }

    private List<ProductRecommendationItemResponse> buildCartFallbackRecommendations(
            CartResponse cart,
            List<ProductResponse> candidates,
            int limit) {
        Map<Long, ProductResponse> productsById = productService.listProducts(true, null, null).stream()
                .collect(Collectors.toMap(ProductResponse::id, product -> product, (left, right) -> left));

        Set<String> resolvedCategories = cart.items().stream()
                .map(CartItemResponse::productId)
                .map(productsById::get)
                .filter(product -> product != null && product.category() != null && !product.category().isBlank())
                .map(ProductResponse::category)
                .collect(Collectors.toSet());

        return candidates.stream()
                .sorted(Comparator
                        .comparing((ProductResponse candidate) -> !resolvedCategories.contains(candidate.category()))
                        .thenComparing(candidate -> !candidate.promoted())
                        .thenComparing(ProductResponse::name))
                .limit(limit)
                .map(product -> new ProductRecommendationItemResponse(
                        product,
                        resolvedCategories.contains(product.category())
                                ? "Ergaenzt die Kategorien aus Ihrem aktuellen Warenkorb."
                                : "Sinnvolle Zusatzempfehlung fuer Ihren Einkauf.",
                        "fallback"))
                .toList();
    }

    private String buildProductSystemPrompt(
            ProductResponse currentProduct,
            List<ProductResponse> candidates,
            User currentUser,
            int limit) {
        StringBuilder builder = new StringBuilder("""
                Du bist ein Empfehlungssystem fuer einen deutschen Webshop.
                Antworte ausschliesslich mit JSON.
                Format:
                {
                  "recommendations": [
                    { "productId": 123, "reason": "Kurze Begruendung auf Deutsch" }
                  ]
                }

                Regeln:
                - Verwende nur productId-Werte aus der Kandidatenliste.
                - Waehle hoechstens %d Produkte aus.
                - Nenne keine Produkte doppelt.
                - Begruendungen muessen kurz, konkret und auf Deutsch sein.
                - Empfiehl nur Produkte, die thematisch oder funktional gut zum aktuellen Produkt passen.
                """.formatted(limit));

        builder.append("\n[AKTUELLES_PRODUKT]\n")
                .append(describeProduct(currentProduct));

        if (currentUser != null) {
            appendUserContext(builder, currentUser);
        }

        builder.append("\n[KANDIDATEN]\n");
        candidates.forEach(candidate -> builder.append(describeProduct(candidate)));
        return builder.toString();
    }

    private String buildCartSystemPrompt(
            CartResponse cart,
            List<ProductResponse> candidates,
            User currentUser,
            int limit) {
        StringBuilder builder = new StringBuilder("""
                Du bist ein Empfehlungssystem fuer einen deutschen Webshop.
                Antworte ausschliesslich mit JSON.
                Format:
                {
                  "recommendations": [
                    { "productId": 123, "reason": "Kurze Begruendung auf Deutsch" }
                  ]
                }

                Regeln:
                - Verwende nur productId-Werte aus der Kandidatenliste.
                - Waehle hoechstens %d Produkte aus.
                - Nenne keine Produkte doppelt.
                - Begruendungen muessen kurz, konkret und auf Deutsch sein.
                - Empfiehl ergaenzende oder passende Zusatzprodukte fuer den Warenkorb.
                """.formatted(limit));

        builder.append("\n[WARENKORB]\n");
        for (CartItemResponse item : cart.items()) {
            builder.append("- ")
                    .append(item.productName())
                    .append(" | Menge: ").append(item.quantity())
                    .append(" | Betrag: ").append(item.lineTotal())
                    .append(" EUR\n");
        }

        appendUserContext(builder, currentUser);

        builder.append("\n[KANDIDATEN]\n");
        candidates.forEach(candidate -> builder.append(describeProduct(candidate)));
        return builder.toString();
    }

    private void appendUserContext(StringBuilder builder, User currentUser) {
        builder.append("\n[NUTZER]\n")
                .append("- Benutzername: ").append(currentUser.getUsername()).append("\n")
                .append("- Kundentyp: ").append(currentUser.getUserType() == UserType.BUSINESS ? "BUSINESS" : "PRIVATE").append("\n");

        List<OrderResponse> recentOrders = orderService.listOrdersForCustomer(currentUser.getId()).stream()
                .limit(5)
                .toList();
        if (!recentOrders.isEmpty()) {
            builder.append("[LETZTE_BESTELLUNGEN]\n");
            for (OrderResponse order : recentOrders) {
                builder.append("- Bestellung #").append(order.id())
                        .append(" | Betrag: ").append(order.totalPrice())
                        .append(" EUR | Produkte: ")
                        .append(order.items().stream()
                                .map(item -> item.productName() + " x" + item.quantity())
                                .collect(Collectors.joining(", ")))
                        .append("\n");
            }
        }
    }

    private String describeProduct(ProductResponse product) {
        return "- productId: " + product.id()
                + " | Name: " + product.name()
                + " | Kategorie: " + defaultText(product.category(), "Ohne Kategorie")
                + " | Promoted: " + (product.promoted() ? "Ja" : "Nein")
                + " | Beschreibung: " + truncate(product.description(), 180)
                + "\n";
    }

    private boolean sameCategory(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private int resolveLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private String sanitizeReason(String reason) {
        String normalized = defaultText(reason, "Passende Empfehlung fuer diesen Kontext.");
        return normalized.length() <= 140 ? normalized : normalized.substring(0, 137) + "...";
    }

    private String truncate(String value, int maxLength) {
        String normalized = defaultText(value, "");
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength - 3) + "...";
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record ParsedRecommendation(long productId, String reason) {}
}
