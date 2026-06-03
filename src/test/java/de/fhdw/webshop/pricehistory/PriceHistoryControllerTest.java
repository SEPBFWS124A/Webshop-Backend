package de.fhdw.webshop.pricehistory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.fhdw.webshop.config.GlobalExceptionHandler;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class PriceHistoryControllerTest {

    @Mock
    private ProductPriceHistoryService historyService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);

        PriceHistoryController controller = new PriceHistoryController(historyService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(converter)
                .build();
    }

    // --- Öffentlicher Zugriff (Gast / Kunde) ---

    @Test
    void getHistory_alsGast_liefertNurPublicFelder() throws Exception {
        List<PriceHistoryPublicDto> publicData = List.of(
                new PriceHistoryPublicDto(Instant.parse("2026-05-01T10:00:00Z"), new BigDecimal("899.00")),
                new PriceHistoryPublicDto(Instant.parse("2026-03-15T08:00:00Z"), new BigDecimal("999.00"))
        );
        when(historyService.getPublicHistory(eq(1L), any(Instant.class), any(Instant.class), eq(100)))
                .thenReturn(publicData);

        mockMvc.perform(get("/api/products/1/price-history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].newPrice").value(899.00))
                .andExpect(jsonPath("$[0].changedAt").value("2026-05-01T10:00:00Z"))
                .andExpect(jsonPath("$[0].id").doesNotExist())
                .andExpect(jsonPath("$[0].oldPrice").doesNotExist())
                .andExpect(jsonPath("$[0].changeReason").doesNotExist())
                .andExpect(jsonPath("$[0].changedByName").doesNotExist());

        verify(historyService).getPublicHistory(eq(1L), any(), any(), eq(100));
        verify(historyService, never()).getAdminHistory(any(), any(), any(), anyInt());
    }

    @Test
    void getHistory_alsGast_liefertLeereListeOhne404() throws Exception {
        when(historyService.getPublicHistory(eq(2L), any(Instant.class), any(Instant.class), eq(100)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/products/2/price-history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getHistory_produktNichtGefunden_liefert404() throws Exception {
        when(historyService.getPublicHistory(eq(99L), any(Instant.class), any(Instant.class), eq(100)))
                .thenThrow(new EntityNotFoundException("Produkt nicht gefunden: 99"));

        mockMvc.perform(get("/api/products/99/price-history"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getHistory_limitParameter_wirdWeitergegeben() throws Exception {
        when(historyService.getPublicHistory(eq(1L), any(), any(), eq(50)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/products/1/price-history").param("limit", "50"))
                .andExpect(status().isOk());

        verify(historyService).getPublicHistory(eq(1L), any(), any(), eq(50));
    }

    @Test
    void getHistory_defaultLimit100_wennNichtAngegeben() throws Exception {
        when(historyService.getPublicHistory(eq(1L), any(), any(), eq(100)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/products/1/price-history"))
                .andExpect(status().isOk());

        verify(historyService).getPublicHistory(eq(1L), any(), any(), eq(100));
    }

    // --- Admin-Zugriff ---

    @Test
    void getHistory_adminDto_enthaeltAlleFelder() {
        // In Standalone-MockMvc hat @AuthenticationPrincipal immer null → public Pfad.
        // Admin-Controller-Routing wird im Full-Integration-Test geprüft.
        // Hier verifizieren wir, dass das AdminDto-Record alle Felder korrekt hält.
        PriceHistoryAdminDto dto = new PriceHistoryAdminDto(
                12L,
                Instant.parse("2026-05-01T10:00:00Z"),
                new BigDecimal("999.00"),
                new BigDecimal("899.00"),
                PriceChangeReason.MANUAL,
                "Admin Sandra K."
        );

        org.assertj.core.api.Assertions.assertThat(dto.id()).isEqualTo(12L);
        org.assertj.core.api.Assertions.assertThat(dto.oldPrice()).isEqualByComparingTo("999.00");
        org.assertj.core.api.Assertions.assertThat(dto.newPrice()).isEqualByComparingTo("899.00");
        org.assertj.core.api.Assertions.assertThat(dto.changeReason()).isEqualTo(PriceChangeReason.MANUAL);
        org.assertj.core.api.Assertions.assertThat(dto.changedByName()).isEqualTo("Admin Sandra K.");
    }

    @Test
    void getHistory_rollentrennung_kundeBekommtNurPublicFelder() throws Exception {
        when(historyService.getPublicHistory(eq(3L), any(), any(), eq(100)))
                .thenReturn(List.of(
                        new PriceHistoryPublicDto(Instant.now(), new BigDecimal("499.00"))
                ));

        mockMvc.perform(get("/api/products/3/price-history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].oldPrice").doesNotExist())
                .andExpect(jsonPath("$[0].changeReason").doesNotExist());

        verify(historyService, never()).getAdminHistory(any(), any(), any(), anyInt());
    }
}
