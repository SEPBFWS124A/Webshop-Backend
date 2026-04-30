package de.fhdw.webshop.alerting;

import de.fhdw.webshop.admin.AuditInitiator;
import de.fhdw.webshop.admin.AuditLogService;
import de.fhdw.webshop.alerting.dto.AddKnownEmailRequest;
import de.fhdw.webshop.alerting.dto.AlertEventConfigResponse;
import de.fhdw.webshop.alerting.dto.KnownEmailAddressResponse;
import de.fhdw.webshop.alerting.dto.UpdateAlertEventConfigRequest;
import de.fhdw.webshop.user.User;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AlertConfigService {

    private final AlertEventConfigRepository alertEventConfigRepository;
    private final KnownEmailAddressRepository knownEmailAddressRepository;
    private final AuditLogService auditLogService;

    public List<AlertEventConfigResponse> getAllEventConfigs() {
        return Arrays.stream(AlertEventType.values())
                .map(eventType -> alertEventConfigRepository.findByEventType(eventType)
                        .map(this::toResponse)
                        .orElseGet(() -> toDefaultResponse(eventType)))
                .toList();
    }

    @Transactional
    public AlertEventConfigResponse updateEventConfig(AlertEventType eventType,
                                                      UpdateAlertEventConfigRequest request,
                                                      User currentUser) {
        AlertEventConfig config = alertEventConfigRepository.findByEventType(eventType)
                .orElseGet(() -> new AlertEventConfig(eventType, eventType.getDefaultStrategy(), true));

        config.setEnabled(request.enabled());
        config.setStrategy(request.strategy());

        List<KnownEmailAddress> selectedRecipients = request.recipientIds() == null
                ? List.of()
                : knownEmailAddressRepository.findAllById(request.recipientIds());
        config.setRecipients(selectedRecipients);

        AlertEventConfig savedConfig = alertEventConfigRepository.save(config);
        auditLogService.record(
                currentUser,
                "UPDATE_ALERT_EVENT_CONFIG",
                "AlertEventConfig",
                savedConfig.getId(),
                AuditInitiator.ADMIN,
                "Alert event config updated: " + eventType.name());
        return toResponse(savedConfig);
    }

    public List<KnownEmailAddressResponse> getAllKnownEmails() {
        return knownEmailAddressRepository.findAllByOrderByLabelAsc().stream()
                .map(this::toEmailResponse)
                .toList();
    }

    @Transactional
    public KnownEmailAddressResponse addKnownEmail(AddKnownEmailRequest request, User currentUser) {
        KnownEmailAddress address = new KnownEmailAddress(request.label(), request.email(), false);
        KnownEmailAddress savedAddress = knownEmailAddressRepository.save(address);
        auditLogService.record(
                currentUser,
                "ADD_KNOWN_EMAIL_ADDRESS",
                "KnownEmailAddress",
                savedAddress.getId(),
                AuditInitiator.ADMIN,
                "Known email added: " + savedAddress.getEmail());
        return toEmailResponse(savedAddress);
    }

    @Transactional
    public void deleteKnownEmail(Long id, User currentUser) {
        KnownEmailAddress address = knownEmailAddressRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Known email not found: " + id));
        alertEventConfigRepository.findAll().forEach(config -> config.getRecipients().remove(address));
        knownEmailAddressRepository.delete(address);
        auditLogService.record(
                currentUser,
                "DELETE_KNOWN_EMAIL_ADDRESS",
                "KnownEmailAddress",
                id,
                AuditInitiator.ADMIN,
                "Known email deleted: " + address.getEmail());
    }

    private AlertEventConfigResponse toResponse(AlertEventConfig config) {
        List<KnownEmailAddressResponse> recipientResponses = config.getRecipients().stream()
                .map(this::toEmailResponse)
                .toList();
        return new AlertEventConfigResponse(
                config.getEventType().name(),
                config.getEventType().getDisplayName(),
                config.isEnabled(),
                config.getStrategy(),
                recipientResponses
        );
    }

    private AlertEventConfigResponse toDefaultResponse(AlertEventType eventType) {
        return new AlertEventConfigResponse(
                eventType.name(),
                eventType.getDisplayName(),
                true,
                eventType.getDefaultStrategy(),
                List.of()
        );
    }

    private KnownEmailAddressResponse toEmailResponse(KnownEmailAddress address) {
        return new KnownEmailAddressResponse(
                address.getId(),
                address.getLabel(),
                address.getEmail(),
                address.isDefault()
        );
    }
}
