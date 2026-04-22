package de.fhdw.webshop.alerting;

public enum AlertEventType {
    HIGH_ERROR_RATE("Hohe HTTP-Fehlerquote", RecipientStrategy.ADMIN_DEFAULT),
    HIGH_HEAP_USAGE("Hohe JVM-Heap-Auslastung", RecipientStrategy.ADMIN_DEFAULT),
    PRODUCT_AVAILABLE("Produkt wieder verfügbar", RecipientStrategy.ACCOUNT_BASED);

    private final String displayName;
    private final RecipientStrategy defaultStrategy;

    AlertEventType(String displayName, RecipientStrategy defaultStrategy) {
        this.displayName = displayName;
        this.defaultStrategy = defaultStrategy;
    }

    public String getDisplayName() {
        return displayName;
    }

    public RecipientStrategy getDefaultStrategy() {
        return defaultStrategy;
    }
}
