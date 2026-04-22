package de.fhdw.webshop.alerting;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AlertingDataInitializer implements ApplicationRunner {

    private final KnownEmailAddressRepository knownEmailAddressRepository;
    private final AlertEventConfigRepository alertEventConfigRepository;

    @Value("${alert.admin-email:}")
    private String adminEmailsRaw;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedKnownEmailAddresses();
        seedAlertEventConfigs();
    }

    private void seedKnownEmailAddresses() {
        if (adminEmailsRaw == null || adminEmailsRaw.isBlank()) return;

        String[] addresses = adminEmailsRaw.split(",");
        for (String raw : addresses) {
            String address = raw.strip();
            if (!address.isBlank() && !knownEmailAddressRepository.existsByEmail(address)) {
                knownEmailAddressRepository.save(new KnownEmailAddress("Admin (" + address + ")", address, true));
                log.info("Seeded known email address: {}", address);
            }
        }
    }

    private void seedAlertEventConfigs() {
        List<KnownEmailAddress> defaultRecipients = knownEmailAddressRepository.findAll().stream()
                .filter(KnownEmailAddress::isDefault)
                .toList();

        for (AlertEventType eventType : AlertEventType.values()) {
            if (alertEventConfigRepository.findByEventType(eventType).isEmpty()) {
                AlertEventConfig config = new AlertEventConfig(
                        eventType, eventType.getDefaultStrategy(), true);
                if (eventType.getDefaultStrategy() == RecipientStrategy.ADMIN_DEFAULT) {
                    config.setRecipients(defaultRecipients);
                }
                alertEventConfigRepository.save(config);
                log.info("Seeded default AlertEventConfig for {}", eventType);
            }
        }
    }
}
