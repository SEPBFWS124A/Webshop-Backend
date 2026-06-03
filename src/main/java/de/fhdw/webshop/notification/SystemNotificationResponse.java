package de.fhdw.webshop.notification;

import java.math.BigDecimal;
import java.time.Instant;

public record SystemNotificationResponse(
        Long id,
        SystemNotificationType type,
        Long productId,
        String productName,
        Long recipientUserId,
        BigDecimal changePercent,
        long currentPeriodUnits,
        long previousPeriodUnits,
        boolean read,
        Instant createdAt,
        String message,
        String targetUrl
) {
    public static SystemNotificationResponse from(SystemNotification n) {
        return new SystemNotificationResponse(
                n.getId(),
                n.getType(),
                n.getProductId(),
                n.getProductName(),
                n.getRecipientUser() != null ? n.getRecipientUser().getId() : null,
                n.getChangePercent(),
                n.getCurrentPeriodUnits(),
                n.getPreviousPeriodUnits(),
                n.isRead(),
                n.getCreatedAt(),
                buildMessage(n),
                n.getTargetUrl()
        );
    }

    private static String buildMessage(SystemNotification n) {
        if (n.getCustomMessage() != null && !n.getCustomMessage().isBlank()) {
            return n.getCustomMessage();
        }

        return switch (n.getType()) {
            case SALES_DROP -> String.format(
                    "Verkaufsrückgang: \"%s\" — Vormonat: %d Stk., Aktueller Zeitraum: %d Stk. (%s%%)",
                    n.getProductName(),
                    n.getPreviousPeriodUnits(),
                    n.getCurrentPeriodUnits(),
                    n.getChangePercent() != null ? formatPercent(n.getChangePercent()) : "n/a"
            );
            case SALES_INCREASE -> String.format(
                    "Verkaufsanstieg: \"%s\" — Vormonat: %d Stk., Aktueller Zeitraum: %d Stk. (+%s%%)",
                    n.getProductName(),
                    n.getPreviousPeriodUnits(),
                    n.getCurrentPeriodUnits(),
                    n.getChangePercent() != null ? formatPercent(n.getChangePercent().abs()) : "n/a"
            );
            case ZERO_SALES -> String.format(
                    "Keine Verkäufe: \"%s\" hatte im Beobachtungszeitraum 0 Verkäufe (Vormonat: %d Stk.)",
                    n.getProductName(),
                    n.getPreviousPeriodUnits()
            );
            case PRODUCT_QA_ANSWER -> String.format(
                    "Neue Antwort auf deine Frage zu \"%s\".",
                    n.getProductName()
            );
            case SUPPORT_TICKET_REPLY -> String.format(
                    "Neue Antwort auf dein Support-Ticket \"%s\".",
                    n.getProductName()
            );
            case SUPPORT_TICKET_CLOSED -> String.format(
                    "Dein Support-Ticket \"%s\" wurde geschlossen.",
                    n.getProductName()
            );
            case PRICE_ALERT_TRIGGERED -> String.format(
                    "Preisalarm: \"%s\" hat deinen Wunschpreis erreicht!",
                    n.getProductName()
            );
        };
    }

    private static String formatPercent(BigDecimal value) {
        return String.format("%.1f", value.doubleValue()).replace('.', ',');
    }
}
