package de.fhdw.webshop.notification;

import de.fhdw.webshop.alerting.KnownEmailAddress;
import de.fhdw.webshop.alerting.KnownEmailAddressRepository;
import de.fhdw.webshop.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Sends transactional emails via JavaMailSender (configured in application.properties).
 * All outgoing emails are redirected to the configured default recipients (isDefault=true)
 * so that no real customer or external addresses ever receive messages in this university project.
 * The originally intended recipient is noted in the email body for transparency.
 * If no default recipients are configured, the email is sent back to the sending address (fromAddress)
 * to ensure customer addresses are never reached under any circumstances.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender javaMailSender;
    private final KnownEmailAddressRepository knownEmailAddressRepository;

    @Value("${app.mail.from:noreply@webshop.local}")
    private String fromAddress;

    @Value("${alert.admin-email:}")
    private String adminEmailsRaw;

    /** US #37 — Sales employee sends a free-form email to a customer. */
    public boolean sendEmailToCustomer(User customer, String subject, String body) {
        return sendEmail(customer.getEmail(), subject, body);
    }

    /** Sends a password-change confirmation to the user. */
    public boolean sendPasswordChangedNotification(User user) {
        return sendEmail(user.getEmail(),
                "Your password has been changed",
                "Your webshop account password was recently changed. If this was not you, please contact support.");
    }

    /** Sends a monitoring alert to all configured admin email addresses. */
    public void sendAdminAlert(String subject, String body) {
        if (adminEmailsRaw == null || adminEmailsRaw.isBlank()) {
            log.warn("Admin alert suppressed (alert.admin-email not configured): {}", subject);
            return;
        }
        String[] recipients = adminEmailsRaw.split(",");
        for (String recipient : recipients) {
            sendEmail(recipient.strip(), subject, body);
        }
    }

    /**
     * Sends an email — always redirected to default recipients (isDefault=true).
     * The originally intended address is prepended to the body for transparency.
     * Falls back to fromAddress (the sending account itself) if no default recipients are configured,
     * ensuring external addresses are never contacted under any circumstances.
     */
    public boolean sendEmail(String intendedAddress, String subject, String body) {
        if (intendedAddress == null || intendedAddress.isBlank()) {
            log.warn("Skipping email because no recipient address was provided for subject {}", subject);
            return false;
        }

        List<KnownEmailAddress> defaultRecipients = knownEmailAddressRepository.findAllByIsDefaultTrue();
        String redirectedBody = buildRedirectedBody(intendedAddress, body);

        if (defaultRecipients.isEmpty()) {
            log.warn("No default recipients configured — redirecting to sending address {}", fromAddress);
            return dispatchEmail(fromAddress, subject, redirectedBody);
        }

        boolean allSucceeded = true;
        for (KnownEmailAddress recipient : defaultRecipients) {
            boolean sent = dispatchEmail(recipient.getEmail(), subject, redirectedBody);
            if (!sent) {
                allSucceeded = false;
            }
        }
        return allSucceeded;
    }

    private String buildRedirectedBody(String intendedAddress, String originalBody) {
        return "================================================================\n"
                + "[Universitätsprojekt – E-Mail-Umleitung aktiv]\n"
                + "Ursprünglicher Empfänger: " + intendedAddress + "\n"
                + "================================================================\n\n"
                + originalBody;
    }

    private boolean dispatchEmail(String toAddress, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(toAddress);
            message.setSubject(subject);
            message.setText(body);
            javaMailSender.send(message);
            log.info("Email dispatched to {} (subject: {})", toAddress, subject);
            return true;
        } catch (Exception exception) {
            log.error("Failed to send email to {}: {}", toAddress, exception.getMessage());
            return false;
        }
    }
}
