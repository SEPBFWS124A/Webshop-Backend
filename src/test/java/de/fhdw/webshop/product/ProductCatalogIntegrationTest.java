package de.fhdw.webshop.product;

import de.fhdw.webshop.AbstractIntegrationTest;
import de.fhdw.webshop.product.dto.ProductResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test verifying that the product catalog works end to end against a real
 * PostgreSQL database: Flyway migrations (including seed data) applied, JPA mapping correct,
 * and the customer-facing query returns only purchasable products.
 */
class ProductCatalogIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void flywayMigrationsSeedProductsIntoRealDatabase() {
        // Proves the full stack: Testcontainers PostgreSQL + Flyway migrations + JPA mapping.
        assertThat(productRepository.count()).isPositive();
    }

    @Test
    void listProductsReturnsOnlyPurchasableProductsForCustomers() {
        List<ProductResponse> purchasableProducts = productService.listProducts(true, null, null);

        assertThat(purchasableProducts).isNotEmpty();
        assertThat(purchasableProducts).allMatch(ProductResponse::purchasable);
    }
}
