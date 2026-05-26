package de.fhdw.webshop.cart;

import de.fhdw.webshop.AbstractIntegrationTest;
import de.fhdw.webshop.cart.dto.CartResponse;
import de.fhdw.webshop.product.Product;
import de.fhdw.webshop.product.ProductRepository;
import de.fhdw.webshop.user.User;
import de.fhdw.webshop.user.UserRepository;
import de.fhdw.webshop.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the cart flow against a real PostgreSQL database. Creates its own
 * customer and a plain standard product (controlled test data, not seed data), persists a
 * cart item, then verifies that the cart service loads it and computes the totals
 * (subtotal, tax, shipping). Runs in a transaction that is rolled back after the test,
 * so it leaves no data behind.
 */
@Transactional
class CartFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private CartService cartService;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void addingProductToCartPersistsAndComputesTotals() {
        User customer = createCustomer();
        Product product = createPurchasableProduct();

        CartItem cartItem = new CartItem();
        cartItem.setUser(customer);
        cartItem.setProduct(product);
        cartItem.setQuantity(2);
        cartRepository.save(cartItem);

        CartResponse cart = cartService.getCart(customer.getId());

        assertThat(cart.items()).hasSize(1);
        assertThat(cart.items())
                .anyMatch(item -> item.productName().equals(product.getName()));
        assertThat(cart.total()).isGreaterThan(BigDecimal.ZERO);
    }

    private User createCustomer() {
        // Unique values avoid collisions with seed data and with the unique constraints.
        String uniqueSuffix = String.valueOf(System.nanoTime());
        User customer = new User();
        customer.setUsername("integration-test-user-" + uniqueSuffix);
        customer.setEmail("integration-test-" + uniqueSuffix + "@example.test");
        customer.setPasswordHash("integration-test-hash");
        customer.getRoles().add(UserRole.CUSTOMER);
        return userRepository.save(customer);
    }

    private Product createPurchasableProduct() {
        // A plain STANDARD product (default product type) so the cart calculation needs no
        // extra attributes such as a gift card amount.
        Product product = new Product();
        product.setName("Integration Test Produkt");
        product.setRecommendedRetailPrice(new BigDecimal("49.99"));
        product.setPurchasable(true);
        return productRepository.save(product);
    }
}
