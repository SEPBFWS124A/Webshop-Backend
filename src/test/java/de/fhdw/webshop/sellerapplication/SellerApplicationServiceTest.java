package de.fhdw.webshop.sellerapplication;

import de.fhdw.webshop.admin.AuditLog;
import de.fhdw.webshop.admin.AuditLogRepository;
import de.fhdw.webshop.admin.AuditLogService;
import de.fhdw.webshop.sellerapplication.dto.CreateSellerApplicationRequest;
import de.fhdw.webshop.sellerapplication.dto.SellerApplicationResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SellerApplicationServiceTest {

    @Test
    void createStoresNormalizedSellerApplication() {
        SellerApplicationRepository repository = mock(SellerApplicationRepository.class);
        AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
        AuditLogService auditLogService = new AuditLogService(auditLogRepository);
        SellerApplicationService service = new SellerApplicationService(repository, auditLogService);
        CreateSellerApplicationRequest request = new CreateSellerApplicationRequest(
                "  Studio Nord GmbH  ",
                "  Lea Fischer  ",
                "  lea@studionord.de  ",
                "  +49 521 123456  ",
                "  https://studionord.de  ",
                "  Home & Living  ",
                "  Nachhaltige Wohnaccessoires aus eigener Fertigung.  "
        );

        when(repository.save(org.mockito.ArgumentMatchers.any(SellerApplication.class)))
                .thenAnswer(invocation -> {
                    SellerApplication application = invocation.getArgument(0);
                    application.setId(17L);
                    application.setCreatedAt(Instant.parse("2026-05-10T10:00:00Z"));
                    return application;
                });

        SellerApplicationResponse response = service.create(request);

        ArgumentCaptor<SellerApplication> captor = ArgumentCaptor.forClass(SellerApplication.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getCompanyName()).isEqualTo("Studio Nord GmbH");
        assertThat(captor.getValue().getContactName()).isEqualTo("Lea Fischer");
        assertThat(captor.getValue().getEmail()).isEqualTo("lea@studionord.de");
        assertThat(captor.getValue().getPhone()).isEqualTo("+49 521 123456");
        assertThat(captor.getValue().getWebsite()).isEqualTo("https://studionord.de");
        assertThat(captor.getValue().getProductCategory()).isEqualTo("Home & Living");
        assertThat(captor.getValue().getMessage()).isEqualTo("Nachhaltige Wohnaccessoires aus eigener Fertigung.");
        assertThat(captor.getValue().getStatus()).isEqualTo(SellerApplicationStatus.RECEIVED);

        assertThat(response.id()).isEqualTo(17L);
        assertThat(response.status()).isEqualTo(SellerApplicationStatus.RECEIVED);

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAction()).isEqualTo("CREATE_SELLER_APPLICATION");
        assertThat(auditCaptor.getValue().getEntityType()).isEqualTo("SellerApplication");
        assertThat(auditCaptor.getValue().getEntityId()).isEqualTo(17L);
        assertThat(auditCaptor.getValue().getDetails()).contains("Studio Nord GmbH");
    }

    @Test
    void listAllReturnsNewestApplicationsFirst() {
        SellerApplicationRepository repository = mock(SellerApplicationRepository.class);
        AuditLogService auditLogService = new AuditLogService(mock(AuditLogRepository.class));
        SellerApplicationService service = new SellerApplicationService(repository, auditLogService);

        SellerApplication newest = new SellerApplication();
        newest.setId(2L);
        newest.setCompanyName("Nord Sales");
        newest.setContactName("Maja Koch");
        newest.setEmail("maja@nord-sales.de");
        newest.setProductCategory("Elektronik & Technik");
        newest.setStatus(SellerApplicationStatus.RECEIVED);
        newest.setCreatedAt(Instant.parse("2026-05-10T11:00:00Z"));

        SellerApplication older = new SellerApplication();
        older.setId(1L);
        older.setCompanyName("Studio Süd");
        older.setContactName("Noah Berg");
        older.setEmail("noah@studio-sued.de");
        older.setProductCategory("Möbel & Einrichtung");
        older.setStatus(SellerApplicationStatus.RECEIVED);
        older.setCreatedAt(Instant.parse("2026-05-09T09:00:00Z"));

        when(repository.findAllByOrderByCreatedAtDescIdDesc()).thenReturn(List.of(newest, older));

        List<SellerApplicationResponse> result = service.listAll();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(SellerApplicationResponse::id).containsExactly(2L, 1L);
        assertThat(result).extracting(SellerApplicationResponse::companyName)
                .containsExactly("Nord Sales", "Studio Süd");
    }
}
