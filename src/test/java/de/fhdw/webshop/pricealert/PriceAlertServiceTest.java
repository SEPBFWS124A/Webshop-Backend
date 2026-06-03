package de.fhdw.webshop.pricealert;

import de.fhdw.webshop.notification.EmailService;
import de.fhdw.webshop.notification.SystemNotification;
import de.fhdw.webshop.notification.SystemNotificationService;
import de.fhdw.webshop.product.Product;
import de.fhdw.webshop.product.ProductService;
import de.fhdw.webshop.user.User;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PriceAlertServiceTest {

    private PriceAlertRepository priceAlertRepository;
    private ProductService productService;
    private ProductService.DiscountLookupPort discountLookupPort;
    private SystemNotificationService notificationService;
    private EmailService emailService;
    private PriceAlertService service;

    @BeforeEach
    void setUp() {
        priceAlertRepository = mock(PriceAlertRepository.class);
        productService = mock(ProductService.class);
        discountLookupPort = mock(ProductService.DiscountLookupPort.class);
        notificationService = mock(SystemNotificationService.class);
        emailService = mock(EmailService.class);
        service = new PriceAlertService(
                priceAlertRepository, productService, discountLookupPort,
                notificationService, emailService);
    }

    @Test
    void createPriceAlert_success() {
        User user = testUser(1L);
        Product product = testProduct(10L, "Test Product", true);

        when(productService.loadProduct(10L)).thenReturn(product);
        when(priceAlertRepository.existsDuplicate(1L, 10L, new BigDecimal("49.99"))).thenReturn(false);
        when(discountLookupPort.findBestActiveDiscountPercent(1L, 10L)).thenReturn(BigDecimal.ZERO);
        when(priceAlertRepository.save(any(PriceAlert.class))).thenAnswer(invocation -> {
            PriceAlert alert = invocation.getArgument(0);
            alert.setId(42L);
            alert.setCreatedAt(Instant.now());
            return alert;
        });

        PriceAlertResponse response = service.createPriceAlert(10L,
                new CreatePriceAlertRequest(new BigDecimal("49.99"), true), user);

        assertThat(response.id()).isEqualTo(42L);
        assertThat(response.productId()).isEqualTo(10L);
        assertThat(response.productName()).isEqualTo("Test Product");
        assertThat(response.targetPrice()).isEqualByComparingTo("49.99");
        assertThat(response.active()).isTrue();
        assertThat(response.status()).isEqualTo(PriceAlertStatus.ACTIVE);
        assertThat(response.notifyByEmail()).isTrue();
    }

    @Test
    void createPriceAlert_duplicateThrowsException() {
        User user = testUser(1L);
        Product product = testProduct(10L, "Test Product", true);

        when(productService.loadProduct(10L)).thenReturn(product);
        when(priceAlertRepository.existsDuplicate(1L, 10L, new BigDecimal("49.99"))).thenReturn(true);

        assertThatThrownBy(() -> service.createPriceAlert(10L,
                new CreatePriceAlertRequest(new BigDecimal("49.99"), false), user))
                .isInstanceOf(DuplicatePriceAlertException.class);
    }

    @Test
    void createPriceAlert_nonPurchasableProductThrows() {
        User user = testUser(1L);
        Product product = testProduct(10L, "Hidden Product", false);

        when(productService.loadProduct(10L)).thenReturn(product);

        assertThatThrownBy(() -> service.createPriceAlert(10L,
                new CreatePriceAlertRequest(new BigDecimal("10.00"), false), user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nicht kaufbar");
    }

    @Test
    void updatePriceAlert_deactivateSetsStatusDisabled() {
        User user = testUser(1L);
        PriceAlert alert = testAlert(42L, user, PriceAlertStatus.ACTIVE, true);

        when(priceAlertRepository.findById(42L)).thenReturn(Optional.of(alert));
        when(productService.loadProduct(anyLong())).thenReturn(alert.getProduct());
        when(discountLookupPort.findBestActiveDiscountPercent(anyLong(), anyLong())).thenReturn(BigDecimal.ZERO);
        when(priceAlertRepository.save(any(PriceAlert.class))).thenAnswer(i -> i.getArgument(0));

        PriceAlertResponse response = service.updatePriceAlert(42L,
                new UpdatePriceAlertRequest(null, false, null), user);

        assertThat(response.active()).isFalse();
        assertThat(response.status()).isEqualTo(PriceAlertStatus.DISABLED);
    }

    @Test
    void updatePriceAlert_reactivateResetsStatusToActive() {
        User user = testUser(1L);
        PriceAlert alert = testAlert(42L, user, PriceAlertStatus.DISABLED, false);

        when(priceAlertRepository.findById(42L)).thenReturn(Optional.of(alert));
        when(productService.loadProduct(anyLong())).thenReturn(alert.getProduct());
        when(discountLookupPort.findBestActiveDiscountPercent(anyLong(), anyLong())).thenReturn(BigDecimal.ZERO);
        when(priceAlertRepository.save(any(PriceAlert.class))).thenAnswer(i -> i.getArgument(0));

        PriceAlertResponse response = service.updatePriceAlert(42L,
                new UpdatePriceAlertRequest(null, true, null), user);

        assertThat(response.active()).isTrue();
        assertThat(response.status()).isEqualTo(PriceAlertStatus.ACTIVE);
    }

    @Test
    void updatePriceAlert_otherUserThrows() {
        User owner = testUser(1L);
        User otherUser = testUser(2L);
        PriceAlert alert = testAlert(42L, owner, PriceAlertStatus.ACTIVE, true);

        when(priceAlertRepository.findById(42L)).thenReturn(Optional.of(alert));

        assertThatThrownBy(() -> service.updatePriceAlert(42L,
                new UpdatePriceAlertRequest(null, false, null), otherUser))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void deletePriceAlert_otherUserThrows() {
        User owner = testUser(1L);
        User otherUser = testUser(2L);
        PriceAlert alert = testAlert(42L, owner, PriceAlertStatus.ACTIVE, true);

        when(priceAlertRepository.findById(42L)).thenReturn(Optional.of(alert));

        assertThatThrownBy(() -> service.deletePriceAlert(42L, otherUser))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void checkPriceAlerts_triggersWhenPriceBelowTarget() {
        User user = testUser(1L);
        PriceAlert alert = testAlert(42L, user, PriceAlertStatus.ACTIVE, true);
        alert.setNotifyByEmail(true);

        when(priceAlertRepository.findByStatusAndActiveTrue(PriceAlertStatus.ACTIVE))
                .thenReturn(List.of(alert));
        when(productService.loadProduct(10L)).thenReturn(alert.getProduct());
        when(discountLookupPort.findBestActiveDiscountPercent(1L, 10L)).thenReturn(BigDecimal.ZERO);
        // Product price is 100, target is 49.99 -> won't trigger
        // Let's set product price to 40.00
        alert.getProduct().setRecommendedRetailPrice(new BigDecimal("40.00"));
        when(priceAlertRepository.save(any(PriceAlert.class))).thenAnswer(i -> i.getArgument(0));
        when(notificationService.createPriceAlertNotification(any(), anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(new SystemNotification());

        service.checkPriceAlerts();

        assertThat(alert.getStatus()).isEqualTo(PriceAlertStatus.TRIGGERED);
        assertThat(alert.isActive()).isFalse();
        assertThat(alert.getTriggeredAt()).isNotNull();
        verify(notificationService).createPriceAlertNotification(eq(user), eq(10L), eq("Test Product"), anyString(), eq("/products/10"));
        verify(emailService).sendEmail(eq(user.getEmail()), contains("Preisalarm"), anyString());
    }

    @Test
    void checkPriceAlerts_doesNotTriggerWhenPriceAboveTarget() {
        User user = testUser(1L);
        PriceAlert alert = testAlert(42L, user, PriceAlertStatus.ACTIVE, true);
        alert.setTargetPrice(new BigDecimal("49.99"));
        alert.getProduct().setRecommendedRetailPrice(new BigDecimal("100.00"));

        when(priceAlertRepository.findByStatusAndActiveTrue(PriceAlertStatus.ACTIVE))
                .thenReturn(List.of(alert));
        when(productService.loadProduct(10L)).thenReturn(alert.getProduct());
        when(discountLookupPort.findBestActiveDiscountPercent(1L, 10L)).thenReturn(BigDecimal.ZERO);
        when(priceAlertRepository.save(any(PriceAlert.class))).thenAnswer(i -> i.getArgument(0));

        service.checkPriceAlerts();

        assertThat(alert.getStatus()).isEqualTo(PriceAlertStatus.ACTIVE);
        assertThat(alert.isActive()).isTrue();
        assertThat(alert.getTriggeredAt()).isNull();
        verify(notificationService, never()).createPriceAlertNotification(any(), anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void checkPriceAlerts_noDoubleTriggering() {
        // Already triggered alerts should not be returned by the query
        when(priceAlertRepository.findByStatusAndActiveTrue(PriceAlertStatus.ACTIVE))
                .thenReturn(List.of());

        service.checkPriceAlerts();

        verify(notificationService, never()).createPriceAlertNotification(any(), anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void checkAlertsForProduct_triggersWhenPriceDropsBelowTarget() {
        User user = testUser(1L);
        PriceAlert alert = testAlert(42L, user, PriceAlertStatus.ACTIVE, true);
        alert.setNotifyByEmail(false);
        alert.getProduct().setRecommendedRetailPrice(new BigDecimal("30.00")); // below target of 49.99

        when(priceAlertRepository.findByProductIdAndStatusAndActiveTrue(10L, PriceAlertStatus.ACTIVE))
                .thenReturn(List.of(alert));
        when(productService.loadProduct(10L)).thenReturn(alert.getProduct());
        when(discountLookupPort.findBestActiveDiscountPercent(1L, 10L)).thenReturn(BigDecimal.ZERO);
        when(priceAlertRepository.save(any(PriceAlert.class))).thenAnswer(i -> i.getArgument(0));
        when(notificationService.createPriceAlertNotification(any(), anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(new SystemNotification());

        service.checkAlertsForProduct(10L);

        assertThat(alert.getStatus()).isEqualTo(PriceAlertStatus.TRIGGERED);
        assertThat(alert.isActive()).isFalse();
        assertThat(alert.getTriggeredAt()).isNotNull();
        assertThat(alert.getLastCheckedAt()).isNotNull();
        verify(notificationService).createPriceAlertNotification(eq(user), eq(10L), eq("Test Product"), anyString(), eq("/products/10"));
        verify(emailService, never()).sendEmail(anyString(), anyString(), anyString());
    }

    @Test
    void checkAlertsForProduct_doesNotTriggerWhenPriceAboveTarget() {
        User user = testUser(1L);
        PriceAlert alert = testAlert(42L, user, PriceAlertStatus.ACTIVE, true);
        alert.getProduct().setRecommendedRetailPrice(new BigDecimal("100.00")); // above target of 49.99

        when(priceAlertRepository.findByProductIdAndStatusAndActiveTrue(10L, PriceAlertStatus.ACTIVE))
                .thenReturn(List.of(alert));
        when(productService.loadProduct(10L)).thenReturn(alert.getProduct());
        when(discountLookupPort.findBestActiveDiscountPercent(1L, 10L)).thenReturn(BigDecimal.ZERO);
        when(priceAlertRepository.save(any(PriceAlert.class))).thenAnswer(i -> i.getArgument(0));

        service.checkAlertsForProduct(10L);

        assertThat(alert.getStatus()).isEqualTo(PriceAlertStatus.ACTIVE);
        assertThat(alert.isActive()).isTrue();
        assertThat(alert.getTriggeredAt()).isNull();
        assertThat(alert.getLastCheckedAt()).isNotNull();
        verify(notificationService, never()).createPriceAlertNotification(any(), anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void checkAlertsForProduct_triggersExactlyAtTargetPrice() {
        User user = testUser(1L);
        PriceAlert alert = testAlert(42L, user, PriceAlertStatus.ACTIVE, true);
        alert.setTargetPrice(new BigDecimal("49.99"));
        alert.getProduct().setRecommendedRetailPrice(new BigDecimal("49.99")); // exactly at target

        when(priceAlertRepository.findByProductIdAndStatusAndActiveTrue(10L, PriceAlertStatus.ACTIVE))
                .thenReturn(List.of(alert));
        when(productService.loadProduct(10L)).thenReturn(alert.getProduct());
        when(discountLookupPort.findBestActiveDiscountPercent(1L, 10L)).thenReturn(BigDecimal.ZERO);
        when(priceAlertRepository.save(any(PriceAlert.class))).thenAnswer(i -> i.getArgument(0));
        when(notificationService.createPriceAlertNotification(any(), anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(new SystemNotification());

        service.checkAlertsForProduct(10L);

        assertThat(alert.getStatus()).isEqualTo(PriceAlertStatus.TRIGGERED);
        assertThat(alert.isActive()).isFalse();
    }

    @Test
    void checkAlertsForProduct_multipleAlertsForSameProduct() {
        User user1 = testUser(1L);
        User user2 = testUser(2L);
        PriceAlert alert1 = testAlert(42L, user1, PriceAlertStatus.ACTIVE, true);
        alert1.setTargetPrice(new BigDecimal("50.00"));
        PriceAlert alert2 = testAlert(43L, user2, PriceAlertStatus.ACTIVE, true);
        alert2.setTargetPrice(new BigDecimal("30.00"));

        // Price drops to 40 -> alert1 triggers (target 50 >= 40), alert2 does not (target 30 < 40)
        Product product = alert1.getProduct();
        product.setRecommendedRetailPrice(new BigDecimal("40.00"));
        alert2.setProduct(product);

        when(priceAlertRepository.findByProductIdAndStatusAndActiveTrue(10L, PriceAlertStatus.ACTIVE))
                .thenReturn(List.of(alert1, alert2));
        when(productService.loadProduct(10L)).thenReturn(product);
        when(discountLookupPort.findBestActiveDiscountPercent(1L, 10L)).thenReturn(BigDecimal.ZERO);
        when(discountLookupPort.findBestActiveDiscountPercent(2L, 10L)).thenReturn(BigDecimal.ZERO);
        when(priceAlertRepository.save(any(PriceAlert.class))).thenAnswer(i -> i.getArgument(0));
        when(notificationService.createPriceAlertNotification(any(), anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(new SystemNotification());

        service.checkAlertsForProduct(10L);

        assertThat(alert1.getStatus()).isEqualTo(PriceAlertStatus.TRIGGERED);
        assertThat(alert1.isActive()).isFalse();
        assertThat(alert2.getStatus()).isEqualTo(PriceAlertStatus.ACTIVE);
        assertThat(alert2.isActive()).isTrue();
    }

    @Test
    void checkAlertsForProduct_withDiscountTriggersAlert() {
        User user = testUser(1L);
        PriceAlert alert = testAlert(42L, user, PriceAlertStatus.ACTIVE, true);
        alert.setTargetPrice(new BigDecimal("50.00"));
        alert.getProduct().setRecommendedRetailPrice(new BigDecimal("100.00")); // base price 100

        when(priceAlertRepository.findByProductIdAndStatusAndActiveTrue(10L, PriceAlertStatus.ACTIVE))
                .thenReturn(List.of(alert));
        when(productService.loadProduct(10L)).thenReturn(alert.getProduct());
        // 50% discount -> effective price = 50.00 which equals target
        when(discountLookupPort.findBestActiveDiscountPercent(1L, 10L)).thenReturn(new BigDecimal("50"));
        when(priceAlertRepository.save(any(PriceAlert.class))).thenAnswer(i -> i.getArgument(0));
        when(notificationService.createPriceAlertNotification(any(), anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(new SystemNotification());

        service.checkAlertsForProduct(10L);

        assertThat(alert.getStatus()).isEqualTo(PriceAlertStatus.TRIGGERED);
        assertThat(alert.isActive()).isFalse();
    }

    @Test
    void checkAlertsForProduct_sendsEmailWhenNotifyByEmailEnabled() {
        User user = testUser(1L);
        PriceAlert alert = testAlert(42L, user, PriceAlertStatus.ACTIVE, true);
        alert.setNotifyByEmail(true);
        alert.getProduct().setRecommendedRetailPrice(new BigDecimal("30.00"));

        when(priceAlertRepository.findByProductIdAndStatusAndActiveTrue(10L, PriceAlertStatus.ACTIVE))
                .thenReturn(List.of(alert));
        when(productService.loadProduct(10L)).thenReturn(alert.getProduct());
        when(discountLookupPort.findBestActiveDiscountPercent(1L, 10L)).thenReturn(BigDecimal.ZERO);
        when(priceAlertRepository.save(any(PriceAlert.class))).thenAnswer(i -> i.getArgument(0));
        when(notificationService.createPriceAlertNotification(any(), anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(new SystemNotification());

        service.checkAlertsForProduct(10L);

        verify(emailService).sendEmail(eq("user1@test.de"), contains("Preisalarm"), anyString());
    }

    @Test
    void checkAlertsForProduct_noAlertsForProduct() {
        when(priceAlertRepository.findByProductIdAndStatusAndActiveTrue(99L, PriceAlertStatus.ACTIVE))
                .thenReturn(List.of());

        service.checkAlertsForProduct(99L);

        verify(notificationService, never()).createPriceAlertNotification(any(), anyLong(), anyString(), anyString(), anyString());
        verify(priceAlertRepository, never()).save(any());
    }

    // ── Helper methods ──────────────────────────────────────────────────────────

    private User testUser(Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("testuser" + id);
        user.setEmail("user" + id + "@test.de");
        return user;
    }

    private Product testProduct(Long id, String name, boolean purchasable) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setPurchasable(purchasable);
        product.setRecommendedRetailPrice(new BigDecimal("100.00"));
        return product;
    }

    private PriceAlert testAlert(Long id, User user, PriceAlertStatus status, boolean active) {
        PriceAlert alert = new PriceAlert();
        alert.setId(id);
        alert.setUser(user);
        alert.setProduct(testProduct(10L, "Test Product", true));
        alert.setTargetPrice(new BigDecimal("49.99"));
        alert.setStatus(status);
        alert.setActive(active);
        alert.setCreatedAt(Instant.now());
        return alert;
    }
}

