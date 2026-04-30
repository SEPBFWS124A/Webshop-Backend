package de.fhdw.webshop.alerting;

import de.fhdw.webshop.alerting.dto.AddKnownEmailRequest;
import de.fhdw.webshop.alerting.dto.AlertEventConfigResponse;
import de.fhdw.webshop.alerting.dto.KnownEmailAddressResponse;
import de.fhdw.webshop.alerting.dto.UpdateAlertEventConfigRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import de.fhdw.webshop.user.User;

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
            @Valid @RequestBody UpdateAlertEventConfigRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(alertConfigService.updateEventConfig(eventType, request, currentUser));
    }

    @GetMapping("/emails")
    public ResponseEntity<List<KnownEmailAddressResponse>> getKnownEmails() {
        return ResponseEntity.ok(alertConfigService.getAllKnownEmails());
    }

    @PostMapping("/emails")
    public ResponseEntity<KnownEmailAddressResponse> addKnownEmail(
            @Valid @RequestBody AddKnownEmailRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(alertConfigService.addKnownEmail(request, currentUser));
    }

    @DeleteMapping("/emails/{id}")
    public ResponseEntity<Void> deleteKnownEmail(@PathVariable Long id,
                                                 @AuthenticationPrincipal User currentUser) {
        alertConfigService.deleteKnownEmail(id, currentUser);
        return ResponseEntity.noContent().build();
    }
}
