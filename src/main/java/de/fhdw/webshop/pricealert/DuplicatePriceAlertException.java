package de.fhdw.webshop.pricealert;

/**
 * Thrown when a user attempts to create a duplicate price alert
 * (same product + same target price + already active).
 */
public class DuplicatePriceAlertException extends RuntimeException {
    public DuplicatePriceAlertException(String message) {
        super(message);
    }
}

