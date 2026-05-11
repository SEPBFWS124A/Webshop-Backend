package de.fhdw.webshop.advertisement;

import de.fhdw.webshop.admin.AuditLogService;
import de.fhdw.webshop.advertisement.dto.AdvertisementRequest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AdvertisementServiceTest {

    @Test
    void createRejectsEndDateBeforeStartDate() {
        AdvertisementRepository repository = mock(AdvertisementRepository.class);
        AdvertisementService service = new AdvertisementService(repository, mock(AuditLogService.class));

        AdvertisementRequest request = new AdvertisementRequest(
                "Geplante Kampagne",
                "Diese Kampagne hat einen ungültigen Zeitraum.",
                AdvertisementType.TEXT,
                null,
                "/products",
                true,
                LocalDate.of(2026, 5, 20),
                LocalDate.of(2026, 5, 19)
        );

        assertThatThrownBy(() -> service.create(request, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Enddatum");

        verify(repository, never()).save(any(Advertisement.class));
    }
}
