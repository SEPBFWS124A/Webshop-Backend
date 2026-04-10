package de.fhdw.webshop.user;

/**
 * Roles that can be assigned to a user account.
 * CUSTOMER: regular end-user who can browse products and place orders.
 * EMPLOYEE: internal staff with read access to customer and product data.
 * SALES_EMPLOYEE: employee with additional authority to manage pricing, discounts, and promotions.
 * ADMIN: full system access including user management and audit log.
 */
public enum UserRole {
    CUSTOMER,
    EMPLOYEE,
    SALES_EMPLOYEE,
    ADMIN
}
