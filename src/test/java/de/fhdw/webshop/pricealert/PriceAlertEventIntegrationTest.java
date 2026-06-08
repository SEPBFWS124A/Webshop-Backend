package de.fhdw.webshop.pricealert;

import de.fhdw.webshop.admin.AuditLogService;
import de.fhdw.webshop.pricehistory.ProductPriceHistoryService;
import de.fhdw.webshop.product.Product;
import de.fhdw.webshop.product.ProductRepository;
import de.fhdw.webshop.product.ProductService;
import de.fhdw.webshop.product.dto.UpdatePriceRequest;
import de.fhdw.webshop.reservation.StockReservationService;
import de.fhdw.webshop.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests that ProductService publishes ProductPriceChangedEvent
 * when the price is updated, and that the EventListener properly delegates.
 */
class PriceAlertEventIntegrationTest {

    private ProductRepository productRepository;
    private ApplicationEventPublisher eventPublisher;
    private ProductService productService;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        productService = new ProductService(
                productRepository,
                mock(AuditLogService.class),
                mock(StockReservationService.class),
                eventPublisher,
                mock(ProductPriceHistoryService.class)
        );
    }

    @Test
    void updatePrice_publishesProductPriceChangedEvent() {
        Product product = new Product();
        product.setId(10L);
        product.setName("Laptop Pro 15");
        product.setRecommendedRetailPrice(new BigDecimal("899.00"));
        product.setPurchasable(true);

        when(productRepository.findById(10L)).thenReturn(java.util.Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(i -> i.getArgument(0));

        User admin = new User();
        admin.setId(99L);
        admin.setUsername("admin");

        productService.updatePrice(10L, new UpdatePriceRequest(new BigDecimal("499.00")), admin);

        ArgumentCaptor<ProductPriceChangedEvent> captor = ArgumentCaptor.forClass(ProductPriceChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        ProductPriceChangedEvent event = captor.getValue();
        assertThat(event.productId()).isEqualTo(10L);
    }

    @Test
    void updatePrice_samePrice_stillPublishesEvent() {
        // Even if price is technically the same, updatePrice always publishes
        Product product = new Product();
        product.setId(10L);
        product.setName("Laptop Pro 15");
        product.setRecommendedRetailPrice(new BigDecimal("899.00"));
        product.setPurchasable(true);

        when(productRepository.findById(10L)).thenReturn(java.util.Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(i -> i.getArgument(0));

        User admin = new User();
        admin.setId(99L);
        admin.setUsername("admin");

        productService.updatePrice(10L, new UpdatePriceRequest(new BigDecimal("899.00")), admin);

        verify(eventPublisher).publishEvent(any(ProductPriceChangedEvent.class));
    }

    @Test
    void eventListener_delegatesToService() {
        PriceAlertService priceAlertService = mock(PriceAlertService.class);
        PriceAlertEventListener listener = new PriceAlertEventListener(priceAlertService);

        listener.onPriceChanged(new ProductPriceChangedEvent(10L));

        verify(priceAlertService).checkAlertsForProduct(10L);
    }

    @Test
    void eventListener_handlesMultipleEventsIndependently() {
        PriceAlertService priceAlertService = mock(PriceAlertService.class);
        PriceAlertEventListener listener = new PriceAlertEventListener(priceAlertService);

        listener.onPriceChanged(new ProductPriceChangedEvent(10L));
        listener.onPriceChanged(new ProductPriceChangedEvent(20L));
        listener.onPriceChanged(new ProductPriceChangedEvent(10L));

        verify(priceAlertService, times(2)).checkAlertsForProduct(10L);
        verify(priceAlertService, times(1)).checkAlertsForProduct(20L);
    }
}

