package de.fhdw.webshop.notification;

public enum SystemNotificationType {
    /** Verkaufsrückgang um mehr als den konfigurierten Schwellenwert. */
    SALES_DROP,
    /** Verkaufsanstieg um mehr als den konfigurierten Schwellenwert. */
    SALES_INCREASE,
    /** Keine einzige Einheit verkauft im Beobachtungszeitraum. */
    ZERO_SALES
}
