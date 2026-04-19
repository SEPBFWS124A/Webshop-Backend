package de.fhdw.webshop.chat;

import de.fhdw.webshop.cart.CartService;
import de.fhdw.webshop.cart.dto.CartResponse;
import de.fhdw.webshop.chat.dto.ChatMessageRequest;
import de.fhdw.webshop.chat.dto.ChatMessageResponse;
import de.fhdw.webshop.order.OrderService;
import de.fhdw.webshop.order.dto.OrderResponse;
import de.fhdw.webshop.product.ProductService;
import de.fhdw.webshop.product.dto.ProductResponse;
import de.fhdw.webshop.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final OllamaClient ollamaClient;
    private final ProductService productService;
    private final CartService cartService;
    private final OrderService orderService;

    public ChatMessageResponse processMessage(User currentUser, ChatMessageRequest request) {
        String systemPrompt = buildSystemPrompt(currentUser);
        List<de.fhdw.webshop.chat.dto.ConversationEntry> historyWithCurrentMessage =
                buildHistoryWithCurrentMessage(request);

        String reply = ollamaClient.chat(systemPrompt, historyWithCurrentMessage);
        return new ChatMessageResponse(reply);
    }

    private String buildSystemPrompt(User currentUser) {
        StringBuilder systemPromptBuilder = new StringBuilder();

        systemPromptBuilder.append("""
                Du bist Shoppi, der freundliche KI-Assistent dieses Webshops.
                Antworte immer auf Deutsch, präzise, freundlich und hilfsbereit.

                Was du tust:
                - Du hilfst Kunden bei Produktfragen, Preisinfos und der Suche nach Artikeln.
                - Du erklärst Bestellhistorie, Warenkorb und Daueraufträge (nur für eingeloggte Nutzer).
                - Du gibst allgemeine Infos zum Shop.

                Was du NICHT tust:
                - Du änderst KEINE Daten (keine Bestellungen, kein Warenkorb, keine Profiledaten).
                - Du gibst KEINE Daten anderer Nutzer preis.
                - Du antwortest NICHT zu Admin- oder Mitarbeiterthemen.
                - Du nennst NIEMALS Passwörter, vollständige Zahlungsdaten oder interne System-IDs.
                - Du führst KEINE Aktionen außerhalb des Webshops aus.

                Wenn du etwas nicht weißt, sage es ehrlich.

                """);

        appendProductCatalogContext(systemPromptBuilder);

        if (currentUser != null) {
            appendUserContext(systemPromptBuilder, currentUser);
        } else {
            systemPromptBuilder.append("""
                    [NUTZERSTATUS]: Nicht eingeloggt.
                    Für personalisierte Infos (Warenkorb, Bestellhistorie, Rabatte) muss sich der Nutzer einloggen.
                    """);
        }

        return systemPromptBuilder.toString();
    }

    private void appendProductCatalogContext(StringBuilder builder) {
        try {
            List<ProductResponse> allProducts = productService.listProducts(null, null, null);
            if (allProducts.isEmpty()) {
                builder.append("[PRODUKTKATALOG]: Derzeit sind keine Produkte im Katalog vorhanden. " +
                        "Weise den Nutzer darauf hin, falls er nach Produkten fragt.\n\n");
                return;
            }
            builder.append("[PRODUKTKATALOG - alle ").append(allProducts.size()).append(" Artikel]:\n");
            for (ProductResponse product : allProducts) {
                builder.append("- ")
                        .append(product.name())
                        .append(" | Kategorie: ").append(product.category())
                        .append(" | Preis: ").append(product.recommendedRetailPrice()).append(" EUR")
                        .append(" | Beschreibung: ").append(truncate(product.description(), 120))
                        .append(" | Kaufbar: ").append(product.purchasable() ? "Ja" : "Nein")
                        .append("\n");
            }
            builder.append("\n");
        } catch (Exception exception) {
            builder.append("[PRODUKTKATALOG]: Konnte nicht geladen werden.\n\n");
        }
    }

    private void appendUserContext(StringBuilder builder, User currentUser) {
        builder.append("[NUTZERPROFIL]:\n")
                .append("- Benutzername: ").append(currentUser.getUsername()).append("\n")
                .append("- Kundennummer: ").append(currentUser.getCustomerNumber()).append("\n")
                .append("- Kundentyp: ").append(currentUser.getUserType() == de.fhdw.webshop.user.UserType.BUSINESS
                        ? "Unternehmenskunde" : "Privatkunde")
                .append("\n\n");

        appendCartContext(builder, currentUser);
        appendOrderHistoryContext(builder, currentUser);
    }

    private void appendCartContext(StringBuilder builder, User currentUser) {
        try {
            CartResponse cart = cartService.getCart(currentUser.getId());
            if (cart.items().isEmpty()) {
                builder.append("[WARENKORB]: Leer.\n\n");
            } else {
                builder.append("[WARENKORB] (").append(cart.items().size()).append(" Artikel):\n");
                cart.items().forEach(item ->
                        builder.append("- ").append(item.productName())
                                .append(" x").append(item.quantity())
                                .append(" = ").append(item.lineTotal()).append(" EUR\n")
                );
                builder.append("Gesamtbetrag: ").append(cart.total()).append(" EUR\n\n");
            }
        } catch (Exception exception) {
            builder.append("[WARENKORB]: Konnte nicht geladen werden.\n\n");
        }
    }

    private void appendOrderHistoryContext(StringBuilder builder, User currentUser) {
        try {
            List<OrderResponse> recentOrders = orderService.listOrdersForCustomer(currentUser.getId())
                    .stream().limit(5).toList();
            if (recentOrders.isEmpty()) {
                builder.append("[BESTELLHISTORIE]: Keine Bestellungen vorhanden.\n\n");
            } else {
                builder.append("[BESTELLHISTORIE] (letzte ").append(recentOrders.size()).append(" Bestellungen):\n");
                recentOrders.forEach(order ->
                        builder.append("- Bestellung #").append(order.id())
                                .append(" | Status: ").append(order.status())
                                .append(" | Betrag: ").append(order.totalPrice()).append(" EUR")
                                .append(" | Datum: ").append(order.createdAt()).append("\n")
                );
                builder.append("\n");
            }
        } catch (Exception exception) {
            builder.append("[BESTELLHISTORIE]: Konnte nicht geladen werden.\n\n");
        }
    }

    private List<de.fhdw.webshop.chat.dto.ConversationEntry> buildHistoryWithCurrentMessage(
            ChatMessageRequest request) {
        List<de.fhdw.webshop.chat.dto.ConversationEntry> history =
                request.history() != null ? new java.util.ArrayList<>(request.history()) : new java.util.ArrayList<>();
        history.add(new de.fhdw.webshop.chat.dto.ConversationEntry("user", request.message()));
        return history;
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }
}
