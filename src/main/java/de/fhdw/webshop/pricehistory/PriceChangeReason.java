package de.fhdw.webshop.pricehistory;

public enum PriceChangeReason {
    INITIAL,    // Erster Preis bei Produktanlage
    MANUAL,     // Admin-Änderung über UI
    PROMOTION,  // Aktionspreisänderung
    DISCOUNT,   // Rabattänderung
    SYSTEM      // Automatisiert / Import
}
