package de.fhdw.webshop.alerting;

import de.fhdw.webshop.alerting.dto.AddKnownEmailRequest;
import de.fhdw.webshop.alerting.dto.AlertEventConfigResponse;
import de.fhdw.webshop.alerting.dto.KnownEmailAddressResponse;
import de.fhdw.webshop.alerting.dto.UpdateAlertEventConfigRequest;
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

    public List<AlertEventConfigResponse> getAllEventConfigs() {
        return Arrays.stream(AlertEventType.values())
                .map(eventType -> alertEventConfigRepository.findByEventType(eventType)
                        .map(this::toResponse)
                        .orElseGet(() -> toDefaultResponse(eventType)))
                .toList();
    }

    @Transactional
    public AlertEventConfigResponse updateEventConfig(AlertEventType eventType,
                                                      UpdateAlertEventConfigRequest request) {
        AlertEventConfig config = alertEventConfigRepository.findByEventType(eventType)
                .orElseGet(() -> new AlertEventConfig(eventType, eventType.getDefaultStrategy(), true));

        config.setEnabled(request.enabled());
        config.setStrategy(request.strategy());

        List<KnownEmailAddress> selectedRecipients = request.recipientIds() == null
                ? List.of()
                : knownEmailAddressRepository.findAllById(request.recipientIds());
        config.setRecipients(selectedRecipients);

        return toResponse(alertEventConfigRepository.save(config));
    }

    public List<KnownEmailAddressResponse> getAllKnownEmails() {
        return knownEmailAddressRepository.findAllByOrderByLabelAsc().stream()
                .map(this::toEmailResponse)
                .toList();
    }

    @Transactional
    public KnownEmailAddressResponse addKnownEmail(AddKnownEmailRequest request) {
        KnownEmailAddress address = new KnownEmailAddress(request.label(), request.email(), false);
        return toEmailResponse(knownEmailAddressRepository.save(address));
    }

    @Transactional
    public void deleteKnownEmail(Long id) {
        KnownEmailAddress address = knownEmailAddressRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Known email not found: " + id));
        alertEventConfigRepository.findAll().forEach(config -> config.getRecipients().remove(address));
        knownEmailAddressRepository.delete(address);
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
