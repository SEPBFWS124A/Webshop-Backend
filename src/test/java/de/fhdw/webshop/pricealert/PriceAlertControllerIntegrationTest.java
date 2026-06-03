package de.fhdw.webshop.pricealert;

import de.fhdw.webshop.AbstractIntegrationTest;
import de.fhdw.webshop.auth.JwtTokenProvider;
import de.fhdw.webshop.product.Product;
import de.fhdw.webshop.product.ProductRepository;
import de.fhdw.webshop.user.User;
import de.fhdw.webshop.user.UserRepository;
import de.fhdw.webshop.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Set;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class PriceAlertControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PriceAlertRepository priceAlertRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private User customer;
    private Product product;
    private String token;

    @BeforeEach
    void setUp() {
        priceAlertRepository.deleteAll();

        customer = userRepository.findByUsername("customer1").orElseGet(() -> {
            User u = new User();
            u.setUsername("customer1");
            u.setEmail("customer1@test.de");
            u.setPasswordHash("$2a$10$dummyhash");
            u.setRoles(Set.of(UserRole.CUSTOMER));
            return userRepository.save(u);
        });

        product = productRepository.findAll().stream()
                .filter(Product::isPurchasable)
                .findFirst()
                .orElseGet(() -> {
                    Product p = new Product();
                    p.setName("Integration Test Product");
                    p.setRecommendedRetailPrice(new BigDecimal("199.99"));
                    p.setPurchasable(true);
                    return productRepository.save(p);
                });

        token = jwtTokenProvider.generateToken(customer);
    }

    @Test
    void createPriceAlert_returns201() throws Exception {
        mockMvc.perform(post("/api/products/" + product.getId() + "/price-alerts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetPrice": 49.99, "notifyByEmail": true}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.productId").value(product.getId()))
                .andExpect(jsonPath("$.productName").value(product.getName()))
                .andExpect(jsonPath("$.targetPrice").value(49.99))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.notifyByEmail").value(true));
    }

    @Test
    void createPriceAlert_duplicate_returns409() throws Exception {
        // Create first alert
        mockMvc.perform(post("/api/products/" + product.getId() + "/price-alerts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetPrice": 49.99, "notifyByEmail": false}
                                """))
                .andExpect(status().isCreated());

        // Try duplicate
        mockMvc.perform(post("/api/products/" + product.getId() + "/price-alerts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetPrice": 49.99, "notifyByEmail": false}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE"));
    }

    @Test
    void createPriceAlert_invalidTargetPrice_returns400() throws Exception {
        mockMvc.perform(post("/api/products/" + product.getId() + "/price-alerts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetPrice": 0, "notifyByEmail": false}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getMyPriceAlerts_returnsList() throws Exception {
        // Create an alert first
        PriceAlert alert = new PriceAlert();
        alert.setUser(customer);
        alert.setProduct(product);
        alert.setTargetPrice(new BigDecimal("59.99"));
        priceAlertRepository.save(alert);

        mockMvc.perform(get("/api/users/me/price-alerts")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].productId").value(product.getId()));
    }

    @Test
    void updatePriceAlert_deactivate() throws Exception {
        PriceAlert alert = new PriceAlert();
        alert.setUser(customer);
        alert.setProduct(product);
        alert.setTargetPrice(new BigDecimal("59.99"));
        alert = priceAlertRepository.save(alert);

        mockMvc.perform(patch("/api/price-alerts/" + alert.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"active": false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.status").value("DISABLED"));
    }

    @Test
    void deletePriceAlert_returns204() throws Exception {
        PriceAlert alert = new PriceAlert();
        alert.setUser(customer);
        alert.setProduct(product);
        alert.setTargetPrice(new BigDecimal("59.99"));
        alert = priceAlertRepository.save(alert);

        mockMvc.perform(delete("/api/price-alerts/" + alert.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void updatePriceAlert_otherUser_returns404() throws Exception {
        // Create alert for customer
        PriceAlert alert = new PriceAlert();
        alert.setUser(customer);
        alert.setProduct(product);
        alert.setTargetPrice(new BigDecimal("59.99"));
        alert = priceAlertRepository.save(alert);

        // Create another user
        User otherUser = userRepository.findByUsername("customer2").orElseGet(() -> {
            User u = new User();
            u.setUsername("customer2");
            u.setEmail("customer2@test.de");
            u.setPasswordHash("$2a$10$dummyhash");
            u.setRoles(Set.of(UserRole.CUSTOMER));
            return userRepository.save(u);
        });
        String otherToken = jwtTokenProvider.generateToken(otherUser);

        mockMvc.perform(patch("/api/price-alerts/" + alert.getId())
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"active": false}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticated_returns401or403() throws Exception {
        mockMvc.perform(get("/api/users/me/price-alerts"))
                .andExpect(status().is4xxClientError());
    }
}



