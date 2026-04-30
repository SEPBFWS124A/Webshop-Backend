package de.fhdw.webshop.admin.statistics.alerting;

public enum StatisticMetric {
    REVENUE("Umsatz"),
    ORDER_COUNT("Bestellungen"),
    ACTIVE_CUSTOMERS("Aktive Kunden"),
    AVG_ORDER_VALUE("Durchschnittlicher Bestellwert");

    private final String label;

    StatisticMetric(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
