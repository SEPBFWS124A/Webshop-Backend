package de.fhdw.webshop.pricehistory;

import de.fhdw.webshop.product.Product;
import de.fhdw.webshop.product.ProductRepository;
import de.fhdw.webshop.user.User;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductPriceHistoryServiceTest {

    @Mock
    private ProductPriceHistoryRepository historyRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductPriceHistoryService service;

    private Product product;
    private User admin;

    @BeforeEach
    void setUp() {
        product = new Product();
        product.setId(1L);
        product.setName("Laptop Pro 15");
        product.setRecommendedRetailPrice(new BigDecimal("899.00"));

        admin = new User();
        admin.setId(10L);
        admin.setUsername("admin");
    }

    // --- recordInitialPrice ---

    @Test
    void recordInitialPrice_speichertEintragMitNullOldPriceUndReasonINITIAL() {
        service.recordInitialPrice(product, admin);

        ArgumentCaptor<ProductPriceHistory> captor = ArgumentCaptor.forClass(ProductPriceHistory.class);
        verify(historyRepository).save(captor.capture());

        ProductPriceHistory saved = captor.getValue();
        assertThat(saved.getProduct()).isSameAs(product);
        assertThat(saved.getOldPrice()).isNull();
        assertThat(saved.getNewPrice()).isEqualByComparingTo("899.00");
        assertThat(saved.getChangeReason()).isEqualTo(PriceChangeReason.INITIAL);
        assertThat(saved.getChangedBy()).isSameAs(admin);
    }

    @Test
    void recordInitialPrice_akzeptiertNullChangedBy() {
        service.recordInitialPrice(product, null);

        ArgumentCaptor<ProductPriceHistory> captor = ArgumentCaptor.forClass(ProductPriceHistory.class);
        verify(historyRepository).save(captor.capture());
        assertThat(captor.getValue().getChangedBy()).isNull();
    }

    // --- recordPriceChange ---

    @Test
    void recordPriceChange_speichertEintragBeiPreisaenderung() {
        BigDecimal oldPrice = new BigDecimal("999.00");
        BigDecimal newPrice = new BigDecimal("799.00");

        service.recordPriceChange(product, oldPrice, newPrice, PriceChangeReason.MANUAL, admin);

        ArgumentCaptor<ProductPriceHistory> captor = ArgumentCaptor.forClass(ProductPriceHistory.class);
        verify(historyRepository).save(captor.capture());

        ProductPriceHistory saved = captor.getValue();
        assertThat(saved.getOldPrice()).isEqualByComparingTo("999.00");
        assertThat(saved.getNewPrice()).isEqualByComparingTo("799.00");
        assertThat(saved.getChangeReason()).isEqualTo(PriceChangeReason.MANUAL);
        assertThat(saved.getChangedBy()).isSameAs(admin);
    }

    @Test
    void recordPriceChange_keinEintragWennPreisIdentisch() {
        BigDecimal samePrice = new BigDecimal("899.00");

        service.recordPriceChange(product, samePrice, samePrice, PriceChangeReason.MANUAL, admin);

        verify(historyRepository, never()).save(any());
    }

    @Test
    void recordPriceChange_keinEintragWennNewPriceNull() {
        service.recordPriceChange(product, new BigDecimal("899.00"), null, PriceChangeReason.MANUAL, admin);

        verify(historyRepository, never()).save(any());
    }

    @Test
    void recordPriceChange_speichertEintragWennOldPriceNull() {
        // Edge-Case: erster manueller Eintrag ohne bekannten Vorpreis
        service.recordPriceChange(product, null, new BigDecimal("499.00"), PriceChangeReason.SYSTEM, null);

        ArgumentCaptor<ProductPriceHistory> captor = ArgumentCaptor.forClass(ProductPriceHistory.class);
        verify(historyRepository).save(captor.capture());
        assertThat(captor.getValue().getOldPrice()).isNull();
        assertThat(captor.getValue().getNewPrice()).isEqualByComparingTo("499.00");
    }

    @Test
    void recordPriceChange_vergleichtBigDecimalWertNichtReferenz() {
        // new BigDecimal("100.00") != new BigDecimal("100.0") per ==, aber compareTo == 0
        BigDecimal price1 = new BigDecimal("100.00");
        BigDecimal price2 = new BigDecimal("100.0");

        service.recordPriceChange(product, price1, price2, PriceChangeReason.MANUAL, admin);

        verify(historyRepository, never()).save(any());
    }

    // --- getPublicHistory ---

    @Test
    void getPublicHistory_liefertNurChangedAtUndNewPrice() {
        when(productRepository.existsById(1L)).thenReturn(true);
        ProductPriceHistory entry = buildEntry(1L, new BigDecimal("800.00"), Instant.parse("2026-05-01T10:00:00Z"));
        when(historyRepository.findByProductIdAndChangedAtBetweenOrderByChangedAtAsc(any(), any(), any()))
                .thenReturn(List.of(entry));

        List<PriceHistoryPublicDto> result = service.getPublicHistory(1L,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-12-31T00:00:00Z"), 100);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().newPrice()).isEqualByComparingTo("800.00");
        assertThat(result.getFirst().changedAt()).isEqualTo(Instant.parse("2026-05-01T10:00:00Z"));
    }

    @Test
    void getPublicHistory_liefertLeereListe_wennKeineEintraege() {
        when(productRepository.existsById(1L)).thenReturn(true);
        when(historyRepository.findByProductIdAndChangedAtBetweenOrderByChangedAtAsc(any(), any(), any()))
                .thenReturn(List.of());

        List<PriceHistoryPublicDto> result = service.getPublicHistory(1L,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-12-31T00:00:00Z"), 100);

        assertThat(result).isEmpty();
    }

    @Test
    void getPublicHistory_wirft404WennProduktNichtExistiert() {
        when(productRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.getPublicHistory(99L,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-12-31T00:00:00Z"), 100))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void getPublicHistory_respektiertLimitParameter() {
        when(productRepository.existsById(1L)).thenReturn(true);
        List<ProductPriceHistory> manyEntries = List.of(
                buildEntry(1L, new BigDecimal("900.00"), Instant.parse("2026-01-01T00:00:00Z")),
                buildEntry(2L, new BigDecimal("850.00"), Instant.parse("2026-02-01T00:00:00Z")),
                buildEntry(3L, new BigDecimal("800.00"), Instant.parse("2026-03-01T00:00:00Z"))
        );
        when(historyRepository.findByProductIdAndChangedAtBetweenOrderByChangedAtAsc(any(), any(), any()))
                .thenReturn(manyEntries);

        List<PriceHistoryPublicDto> result = service.getPublicHistory(1L,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-12-31T00:00:00Z"), 2);

        assertThat(result).hasSize(2);
    }

    // --- getAdminHistory ---

    @Test
    void getAdminHistory_liefertAlleFelder() {
        when(productRepository.existsById(1L)).thenReturn(true);
        ProductPriceHistory entry = buildEntry(42L, new BigDecimal("799.00"), Instant.parse("2026-05-01T10:00:00Z"));
        entry.setOldPrice(new BigDecimal("999.00"));
        entry.setChangeReason(PriceChangeReason.MANUAL);
        entry.setChangedBy(admin);
        when(historyRepository.findByProductIdAndChangedAtBetweenOrderByChangedAtAsc(any(), any(), any()))
                .thenReturn(List.of(entry));

        List<PriceHistoryAdminDto> result = service.getAdminHistory(1L,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-12-31T00:00:00Z"), 100);

        assertThat(result).hasSize(1);
        PriceHistoryAdminDto dto = result.getFirst();
        assertThat(dto.id()).isEqualTo(42L);
        assertThat(dto.oldPrice()).isEqualByComparingTo("999.00");
        assertThat(dto.newPrice()).isEqualByComparingTo("799.00");
        assertThat(dto.changeReason()).isEqualTo(PriceChangeReason.MANUAL);
        assertThat(dto.changedByName()).isEqualTo("admin");
    }

    @Test
    void getAdminHistory_changedByNameIstNullWennSystemaenderung() {
        when(productRepository.existsById(1L)).thenReturn(true);
        ProductPriceHistory entry = buildEntry(5L, new BigDecimal("500.00"), Instant.now());
        entry.setChangedBy(null); // System-Änderung
        when(historyRepository.findByProductIdAndChangedAtBetweenOrderByChangedAtAsc(any(), any(), any()))
                .thenReturn(List.of(entry));

        List<PriceHistoryAdminDto> result = service.getAdminHistory(1L,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-12-31T00:00:00Z"), 100);

        assertThat(result.getFirst().changedByName()).isNull();
    }

    // --- Hilfsmethode ---

    private ProductPriceHistory buildEntry(Long id, BigDecimal newPrice, Instant changedAt) {
        ProductPriceHistory entry = new ProductPriceHistory();
        entry.setId(id);
        entry.setProduct(product);
        entry.setNewPrice(newPrice);
        entry.setChangedAt(changedAt);
        entry.setChangeReason(PriceChangeReason.MANUAL);
        return entry;
    }
}

