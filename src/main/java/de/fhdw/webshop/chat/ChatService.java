package de.fhdw.webshop.chat;

import de.fhdw.webshop.cart.CartService;
import de.fhdw.webshop.cart.dto.CartResponse;
import de.fhdw.webshop.chat.dto.ChatMessageRequest;
import de.fhdw.webshop.chat.dto.ChatMessageResponse;
import de.fhdw.webshop.followuporder.FollowUpOrderService;
import de.fhdw.webshop.followuporder.dto.FollowUpOrderItemResponse;
import de.fhdw.webshop.followuporder.dto.FollowUpOrderResponse;
import de.fhdw.webshop.notification.SystemNotificationResponse;
import de.fhdw.webshop.notification.SystemNotificationService;
import de.fhdw.webshop.order.OrderService;
import de.fhdw.webshop.order.dto.OrderItemResponse;
import de.fhdw.webshop.order.dto.OrderResponse;
import de.fhdw.webshop.product.ProductService;
import de.fhdw.webshop.product.dto.ProductResponse;
import de.fhdw.webshop.standingorder.dto.StandingOrderItemResponse;
import de.fhdw.webshop.standingorder.dto.StandingOrderResponse;
import de.fhdw.webshop.standingorder.StandingOrderService;
import de.fhdw.webshop.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatService {

    private static final int MAX_RECENT_ORDERS = 5;
    private static final int MAX_RECENT_NOTIFICATIONS = 5;
    private static final int MAX_ITEMS_PER_ORDER = 10;

    private final OllamaClient ollamaClient;
    private final ProductService productService;
    private final CartService cartService;
    private final OrderService orderService;
    private final StandingOrderService standingOrderService;
    private final FollowUpOrderService followUpOrderService;
    private final SystemNotificationService systemNotificationService;

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
                - Du erklärst Warenkorb, Bestellungen (inkl. Liefer- und Bestellstatus),
                  Daueraufträge, Folgebestellungen und Benachrichtigungen (nur für eingeloggte Nutzer).
                - Du gibst allgemeine Infos zum Shop.

                Was du NICHT tust:
                - Du änderst KEINE Daten (keine Bestellungen, kein Warenkorb, keine Profiledaten).
                - Du gibst KEINE Daten anderer Nutzer preis.
                - Du antwortest NICHT zu Admin- oder Mitarbeiterthemen.
                - Du nennst NIEMALS Passwörter, vollständige Zahlungsdaten oder interne System-IDs.
                - Du führst KEINE Aktionen außerhalb des Webshops aus.

                Wenn du etwas nicht weißt, sage es ehrlich.

                Du kannst Markdown-Formatierung verwenden (z. B. **Fettdruck**, Listen mit `-`, Überschriften mit `##`),
                um Antworten übersichtlicher zu gestalten. Setze Markdown gezielt ein, nicht übermäßig.

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
            List<ProductResponse> allProducts = productService.listProducts(true, null, null);
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
        appendStandingOrderContext(builder, currentUser);
        appendFollowUpOrderContext(builder, currentUser);
        appendNotificationContext(builder, currentUser);
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
                    .stream().limit(MAX_RECENT_ORDERS).toList();
            if (recentOrders.isEmpty()) {
                builder.append("[BESTELLHISTORIE]: Keine Bestellungen vorhanden.\n\n");
                return;
            }

            appendOrderStatusLegend(builder);

            builder.append("[BESTELLHISTORIE] (letzte ").append(recentOrders.size()).append(" Bestellungen):\n");
            for (OrderResponse order : recentOrders) {
                appendSingleOrder(builder, order);
            }
            builder.append("\n");
        } catch (Exception exception) {
            builder.append("[BESTELLHISTORIE]: Konnte nicht geladen werden.\n\n");
        }
    }

    private void appendOrderStatusLegend(StringBuilder builder) {
        builder.append("""
                [BESTELLSTATUS-BEDEUTUNG]:
                - PENDING: Bestellung aufgegeben, noch nicht bestätigt.
                - Pending_Approval: Wartet auf Freigabe (z. B. Budgetfreigabe bei Unternehmenskunden).
                - Rejected: Freigabe wurde abgelehnt.
                - CONFIRMED: Bestätigt, wird bearbeitet.
                - PACKED_IN_WAREHOUSE: Im Lager verpackt.
                - READY_FOR_PICKUP: Abholbereit (Click & Collect).
                - IN_TRUCK: Im Lieferfahrzeug unterwegs.
                - SHIPPED: Versandt.
                - DELIVERED: Zugestellt.
                - CANCELLED: Storniert.

                """);
    }

    private void appendSingleOrder(StringBuilder builder, OrderResponse order) {
        String orderLabel = order.orderNumber() != null ? order.orderNumber() : "#" + order.id();
        builder.append("- Bestellung ").append(orderLabel)
                .append(" | Status: ").append(order.status())
                .append(" | Versandart: ").append(order.shippingMethod())
                .append(" | Betrag: ").append(order.totalPrice()).append(" EUR")
                .append(" | Bestelldatum: ").append(order.createdAt());

        if (order.estimatedDeliveryAt() != null) {
            builder.append(" | Voraussichtliche Lieferung: ").append(order.estimatedDeliveryAt());
        }
        if (order.deliveredAt() != null) {
            builder.append(" | Geliefert am: ").append(order.deliveredAt());
        }
        if (order.pickupStore() != null) {
            builder.append(" | Abholfiliale: ").append(order.pickupStore().name())
                    .append(" (").append(order.pickupStore().city()).append(")");
        }
        if (order.truckIdentifier() != null && !order.truckIdentifier().isBlank()) {
            builder.append(" | Sendung: ").append(order.truckIdentifier());
        }
        builder.append("\n");

        appendOrderItems(builder, order);
    }

    private void appendOrderItems(StringBuilder builder, OrderResponse order) {
        if (order.items() == null || order.items().isEmpty()) {
            return;
        }
        List<OrderItemResponse> items = order.items().stream().limit(MAX_ITEMS_PER_ORDER).toList();
        builder.append("    Artikel: ");
        for (int index = 0; index < items.size(); index++) {
            OrderItemResponse item = items.get(index);
            builder.append(item.productName()).append(" x").append(item.quantity());
            if (index < items.size() - 1) {
                builder.append(", ");
            }
        }
        if (order.items().size() > MAX_ITEMS_PER_ORDER) {
            builder.append(", …");
        }
        builder.append("\n");
    }

    private void appendStandingOrderContext(StringBuilder builder, User currentUser) {
        try {
            List<StandingOrderResponse> standingOrders =
                    standingOrderService.listForCustomer(currentUser.getId());
            if (standingOrders.isEmpty()) {
                builder.append("[DAUERAUFTRÄGE]: Keine Daueraufträge vorhanden.\n\n");
                return;
            }
            builder.append("[DAUERAUFTRÄGE] (").append(standingOrders.size()).append("):\n");
            for (StandingOrderResponse standingOrder : standingOrders) {
                builder.append("- Dauerauftrag #").append(standingOrder.id())
                        .append(" | Intervall: alle ").append(standingOrder.intervalValue())
                        .append(" ").append(formatIntervalType(standingOrder))
                        .append(" | Nächste Ausführung: ").append(standingOrder.nextExecutionDate())
                        .append(" | Status: ").append(standingOrder.active() ? "aktiv" : "pausiert");
                appendStandingOrderItems(builder, standingOrder);
                builder.append("\n");
            }
            builder.append("\n");
        } catch (Exception exception) {
            builder.append("[DAUERAUFTRÄGE]: Konnten nicht geladen werden.\n\n");
        }
    }

    private void appendStandingOrderItems(StringBuilder builder, StandingOrderResponse standingOrder) {
        if (standingOrder.items() == null || standingOrder.items().isEmpty()) {
            return;
        }
        builder.append(" | Artikel: ");
        List<StandingOrderItemResponse> items = standingOrder.items();
        for (int index = 0; index < items.size(); index++) {
            StandingOrderItemResponse item = items.get(index);
            builder.append(item.productName()).append(" x").append(item.quantity());
            if (index < items.size() - 1) {
                builder.append(", ");
            }
        }
    }

    private String formatIntervalType(StandingOrderResponse standingOrder) {
        if (standingOrder.intervalType() == null) {
            return "";
        }
        return switch (standingOrder.intervalType()) {
            case DAYS -> "Tag(e)";
            case WEEKS -> "Woche(n)";
            case MONTHS -> "Monat(e)";
            case YEARS -> "Jahr(e)";
        };
    }

    private void appendFollowUpOrderContext(StringBuilder builder, User currentUser) {
        try {
            List<FollowUpOrderResponse> followUpOrders =
                    followUpOrderService.listForCustomer(currentUser.getId());
            if (followUpOrders.isEmpty()) {
                builder.append("[FOLGEBESTELLUNGEN]: Keine Folgebestellungen vorhanden.\n\n");
                return;
            }
            builder.append("[FOLGEBESTELLUNGEN] (").append(followUpOrders.size()).append("):\n");
            for (FollowUpOrderResponse followUpOrder : followUpOrders) {
                builder.append("- Folgebestellung #").append(followUpOrder.id())
                        .append(" | Ausführungsdatum: ").append(followUpOrder.executionDate())
                        .append(" | Status: ").append(formatFollowUpStatus(followUpOrder));
                appendFollowUpOrderItems(builder, followUpOrder);
                builder.append("\n");
            }
            builder.append("\n");
        } catch (Exception exception) {
            builder.append("[FOLGEBESTELLUNGEN]: Konnten nicht geladen werden.\n\n");
        }
    }

    private void appendFollowUpOrderItems(StringBuilder builder, FollowUpOrderResponse followUpOrder) {
        if (followUpOrder.items() == null || followUpOrder.items().isEmpty()) {
            return;
        }
        builder.append(" | Artikel: ");
        List<FollowUpOrderItemResponse> items = followUpOrder.items();
        for (int index = 0; index < items.size(); index++) {
            FollowUpOrderItemResponse item = items.get(index);
            builder.append(item.productName()).append(" x").append(item.quantity());
            if (index < items.size() - 1) {
                builder.append(", ");
            }
        }
    }

    private String formatFollowUpStatus(FollowUpOrderResponse followUpOrder) {
        if (followUpOrder.status() == null) {
            return "unbekannt";
        }
        return switch (followUpOrder.status()) {
            case PENDING -> "ausstehend";
            case EXECUTED -> "ausgeführt";
            case CANCELLED -> "storniert";
        };
    }

    private void appendNotificationContext(StringBuilder builder, User currentUser) {
        try {
            long unreadCount = systemNotificationService.getUnreadCount(currentUser);
            List<SystemNotificationResponse> notifications = systemNotificationService.getAll(currentUser)
                    .stream().limit(MAX_RECENT_NOTIFICATIONS).toList();
            if (notifications.isEmpty()) {
                builder.append("[BENACHRICHTIGUNGEN]: Keine Benachrichtigungen vorhanden.\n\n");
                return;
            }
            builder.append("[BENACHRICHTIGUNGEN] (").append(unreadCount).append(" ungelesen, ")
                    .append("letzte ").append(notifications.size()).append("):\n");
            for (SystemNotificationResponse notification : notifications) {
                builder.append("- ").append(notification.read() ? "[gelesen] " : "[ungelesen] ")
                        .append(notification.message())
                        .append("\n");
            }
            builder.append("\n");
        } catch (Exception exception) {
            builder.append("[BENACHRICHTIGUNGEN]: Konnten nicht geladen werden.\n\n");
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
