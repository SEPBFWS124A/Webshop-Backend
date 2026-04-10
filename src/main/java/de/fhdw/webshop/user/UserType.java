package de.fhdw.webshop.user;

/**
 * Distinguishes between private individuals and business customers (US #38).
 * Business customers may have additional billing/discount rules.
 */
public enum UserType {
    PRIVATE,
    BUSINESS
}
