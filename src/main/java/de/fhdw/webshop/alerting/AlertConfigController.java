package de.fhdw.webshop.alerting;

import de.fhdw.webshop.alerting.dto.AddKnownEmailRequest;
import de.fhdw.webshop.alerting.dto.AlertEventConfigResponse;
import de.fhdw.webshop.alerting.dto.KnownEmailAddressResponse;
import de.fhdw.webshop.alerting.dto.UpdateAlertEventConfigRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/alerting")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AlertConfigController {

    private final AlertConfigService alertConfigService;

    @GetMapping("/events")
    public ResponseEntity<List<AlertEventConfigResponse>> getAlertEvents() {
        return ResponseEntity.ok(alertConfigService.getAllEventConfigs());
    }

    @PutMapping("/events/{eventType}")
    public ResponseEntity<AlertEventConfigResponse> updateAlertEvent(
            @PathVariable AlertEventType eventType,
            @Valid @RequestBody UpdateAlertEventConfigRequest request) {
        return ResponseEntity.ok(alertConfigService.updateEventConfig(eventType, request));
    }

    @GetMapping("/emails")
    public ResponseEntity<List<KnownEmailAddressResponse>> getKnownEmails() {
        return ResponseEntity.ok(alertConfigService.getAllKnownEmails());
    }

    @PostMapping("/emails")
    public ResponseEntity<KnownEmailAddressResponse> addKnownEmail(
            @Valid @RequestBody AddKnownEmailRequest request) {
        return ResponseEntity.ok(alertConfigService.addKnownEmail(request));
    }

    @DeleteMapping("/emails/{id}")
    public ResponseEntity<Void> deleteKnownEmail(@PathVariable Long id) {
        alertConfigService.deleteKnownEmail(id);
        return ResponseEntity.noContent().build();
    }
}
