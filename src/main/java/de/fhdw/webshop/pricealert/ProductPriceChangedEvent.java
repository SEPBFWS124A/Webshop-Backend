package de.fhdw.webshop.pricealert;

/**
 * Application event published when a product's price changes
 * (e.g., price update, new discount, volume discount tier).
 * Triggers immediate price-alert evaluation for the affected product.
 */
public record ProductPriceChangedEvent(Long productId) {}

