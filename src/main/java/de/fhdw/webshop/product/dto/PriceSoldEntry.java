package de.fhdw.webshop.product.dto;

import java.math.BigDecimal;

/** One price point at which a product was sold, together with the total units sold at that price. */
public record PriceSoldEntry(BigDecimal price, long unitsSold) {}
