package de.fhdw.webshop.alerting;

import de.fhdw.webshop.notification.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BusinessEmailService {

    private final AlertEventConfigRepository alertEventConfigRepository;
    private final KnownEmailAddressRepository knownEmailAddressRepository;
    private final EmailService emailService;

    @Value("${alert.admin-email:}")
    private String fallbackAdminEmailsRaw;

    public void sendAlert(AlertEventType eventType, String subject, String body) {
        alertEventConfigRepository.findByEventType(eventType).ifPresentOrElse(
                config -> sendToConfiguredRecipients(config, subject, body),
                () -> {
                    log.warn("No AlertEventConfig found for {}, falling back to admin email", eventType);
                    sendToFallbackRecipients(subject, body);
                }
        );
    }

    private void sendToConfiguredRecipients(AlertEventConfig config, String subject, String body) {
        if (!config.isEnabled()) {
            log.debug("Alert suppressed — event {} is disabled", config.getEventType());
            return;
        }

        List<KnownEmailAddress> recipients = config.getRecipients();
        if (recipients.isEmpty()) {
            log.warn("Alert {} has no recipients configured, falling back to admin email", config.getEventType());
            sendToFallbackRecipients(subject, body);
            return;
        }

        for (KnownEmailAddress recipient : recipients) {
            emailService.sendEmail(recipient.getEmail(), subject, body);
        }
    }

    public void sendTestAlert(String subject, String body) {
        List<KnownEmailAddress> defaultRecipients = knownEmailAddressRepository.findAllByIsDefaultTrue();
        if (defaultRecipients.isEmpty()) {
            log.warn("Test alert suppressed — no default recipients configured (ALERT_ADMIN_EMAIL not set)");
            return;
        }
        for (KnownEmailAddress recipient : defaultRecipients) {
            emailService.sendEmail(recipient.getEmail(), subject, body);
        }
    }

    private void sendToFallbackRecipients(String subject, String body) {
        if (fallbackAdminEmailsRaw == null || fallbackAdminEmailsRaw.isBlank()) {
            log.warn("Alert suppressed — no fallback admin email configured");
            return;
        }
        for (String address : fallbackAdminEmailsRaw.split(",")) {
            emailService.sendEmail(address.strip(), subject, body);
        }
    }
}
