package de.fhdw.webshop.notification;

import de.fhdw.webshop.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    /** US #37 — Sales employee sends a free-form email to a customer. */
    public void sendEmailToCustomer(User customer, String subject, String body) {
        sendEmail(customer.getEmail(), subject, body);
    }

    /** Sends a password-change confirmation to the user. */
    public void sendPasswordChangedNotification(User user) {
        sendEmail(user.getEmail(),
                "Your password has been changed",
                "Your webshop account password was recently changed. If this was not you, please contact support.");
    }

    /** Internal helper — logs and sends. */
    public void sendEmail(String toAddress, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toAddress);
            message.setSubject(subject);
            message.setText(body);
            javaMailSender.send(message);
            log.info("Email sent to {}: {}", toAddress, subject);
        } catch (Exception exception) {
            log.error("Failed to send email to {}: {}", toAddress, exception.getMessage());
        }
    }
}
