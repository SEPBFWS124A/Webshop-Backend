package de.fhdw.webshop.notification;

import de.fhdw.webshop.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends transactional emails via JavaMailSender (configured in application.properties).
 * In local development the mail server points to Mailhog/Mailpit on port 1025.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender javaMailSender;
    @Value("${app.mail.from:noreply@webshop.local}")
    private String fromAddress;

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

    /** Internal helper — logs and sends. */
    public boolean sendEmail(String toAddress, String subject, String body) {
        try {
            if (toAddress == null || toAddress.isBlank()) {
                log.warn("Skipping email because no recipient address was provided for subject {}", subject);
                return false;
            }
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(toAddress);
            message.setSubject(subject);
            message.setText(body);
            javaMailSender.send(message);
            log.info("Email sent to {}: {}", toAddress, subject);
            return true;
        } catch (Exception exception) {
            log.error("Failed to send email to {}: {}", toAddress, exception.getMessage());
            return false;
        }
    }
}
