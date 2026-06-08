package de.fhdw.webshop.notification;

public enum SystemNotificationType {
    /** Verkaufsrückgang um mehr als den konfigurierten Schwellenwert. */
    SALES_DROP,
    /** Verkaufsanstieg um mehr als den konfigurierten Schwellenwert. */
    SALES_INCREASE,
    /** Keine einzige Einheit verkauft im Beobachtungszeitraum. */
    ZERO_SALES,
    /** Antwort auf eine Produktfrage eines Kunden. */
    PRODUCT_QA_ANSWER,
    /** Öffentliche Antwort auf ein Support-Ticket. */
    SUPPORT_TICKET_REPLY,
    /** Support-Ticket wurde geschlossen. */
    SUPPORT_TICKET_CLOSED,
    /** Preisalarm wurde ausgelöst (Preis hat Zielpreis erreicht). */
    PRICE_ALERT_TRIGGERED,
    /** Freunde-werben-Belohnung: Referrer hat einen Gutschein erhalten. */
    REFERRAL_REWARD_EARNED
}
