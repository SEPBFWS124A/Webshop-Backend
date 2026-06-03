package de.fhdw.webshop.notification;

import de.fhdw.webshop.user.User;
import de.fhdw.webshop.user.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SystemNotificationService {

    private final SystemNotificationRepository repository;

    public List<SystemNotificationResponse> getAll(User currentUser) {
        return repositoryFor(currentUser)
                .stream()
                .map(SystemNotificationResponse::from)
                .toList();
    }

    public long getUnreadCount(User currentUser) {
        if (isCustomerOnly(currentUser)) {
            return repository.countByRecipientUserIdAndReadFalse(currentUser.getId());
        }
        return repository.countByRecipientUserIsNullAndReadFalse();
    }

    @Transactional
    public SystemNotificationResponse markAsRead(Long id, User currentUser) {
        SystemNotification notification = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Benachrichtigung nicht gefunden: " + id));
        if (isCustomerOnly(currentUser) && (
                notification.getRecipientUser() == null
                        || !notification.getRecipientUser().getId().equals(currentUser.getId())
        )) {
            throw new IllegalArgumentException("Benachrichtigung nicht gefunden: " + id);
        }
        notification.setRead(true);
        return SystemNotificationResponse.from(repository.save(notification));
    }

    @Transactional
    public void markAllAsRead(User currentUser) {
        if (isCustomerOnly(currentUser)) {
            repository.markAllAsReadForRecipient(currentUser.getId());
            return;
        }
        repository.markAllAsRead();
    }

    /** Erstellt eine neue Systembenachrichtigung (intern vom Scheduler aufgerufen). */
    @Transactional
    public SystemNotification create(SystemNotificationType type,
                                     Long productId,
                                     String productName,
                                     BigDecimal changePercent,
                                     long currentPeriodUnits,
                                     long previousPeriodUnits) {
        SystemNotification notification = new SystemNotification();
        notification.setType(type);
        notification.setProductId(productId);
        notification.setProductName(productName);
        notification.setChangePercent(changePercent);
        notification.setCurrentPeriodUnits(currentPeriodUnits);
        notification.setPreviousPeriodUnits(previousPeriodUnits);
        return repository.save(notification);
    }

    @Transactional
    public SystemNotification createProductQaAnswerNotification(
            User recipient,
            Long productId,
            String productName,
            String answerAuthorName
    ) {
        SystemNotification notification = new SystemNotification();
        notification.setType(SystemNotificationType.PRODUCT_QA_ANSWER);
        notification.setRecipientUser(recipient);
        notification.setProductId(productId);
        notification.setProductName(productName);
        notification.setCurrentPeriodUnits(0);
        notification.setPreviousPeriodUnits(0);
        notification.setCustomMessage(String.format(
                "%s hat deine Frage zu \"%s\" beantwortet.",
                answerAuthorName,
                productName
        ));
        return repository.save(notification);
    }

    @Transactional
    public SystemNotification createSupportTicketReplyNotification(
            User recipient,
            Long ticketId,
            String ticketNumber,
            String subject,
            String authorName
    ) {
        SystemNotification notification = supportTicketNotification(
                recipient,
                SystemNotificationType.SUPPORT_TICKET_REPLY,
                ticketId,
                ticketNumber,
                String.format("%s hat auf dein Support-Ticket %s \"%s\" geantwortet.", authorName, ticketNumber, subject)
        );
        return repository.save(notification);
    }

    @Transactional
    public SystemNotification createSupportTicketClosedNotification(
            User recipient,
            Long ticketId,
            String ticketNumber,
            String subject
    ) {
        SystemNotification notification = supportTicketNotification(
                recipient,
                SystemNotificationType.SUPPORT_TICKET_CLOSED,
                ticketId,
                ticketNumber,
                String.format("Dein Support-Ticket %s \"%s\" wurde geschlossen.", ticketNumber, subject)
        );
        return repository.save(notification);
    }

    private SystemNotification supportTicketNotification(
            User recipient,
            SystemNotificationType type,
            Long ticketId,
            String ticketNumber,
            String message
    ) {
        SystemNotification notification = new SystemNotification();
        notification.setType(type);
        notification.setRecipientUser(recipient);
        notification.setProductName(ticketNumber);
        notification.setCurrentPeriodUnits(0);
        notification.setPreviousPeriodUnits(0);
        notification.setCustomMessage(message);
        notification.setTargetUrl("/support/tickets/" + ticketId);
        return notification;
    }

    @Transactional
    public SystemNotification createPriceAlertNotification(
            User recipient,
            Long productId,
            String productName,
            String message,
            String targetUrl
    ) {
        SystemNotification notification = new SystemNotification();
        notification.setType(SystemNotificationType.PRICE_ALERT_TRIGGERED);
        notification.setRecipientUser(recipient);
        notification.setProductId(productId);
        notification.setProductName(productName);
        notification.setCurrentPeriodUnits(0);
        notification.setPreviousPeriodUnits(0);
        notification.setCustomMessage(message);
        notification.setTargetUrl(targetUrl);
        return repository.save(notification);
    }

    private List<SystemNotification> repositoryFor(User currentUser) {
        if (isCustomerOnly(currentUser)) {
            return repository.findByRecipientUserIdOrderByCreatedAtDesc(currentUser.getId());
        }
        return repository.findByRecipientUserIsNullOrderByCreatedAtDesc();
    }

    private boolean isCustomerOnly(User currentUser) {
        return currentUser != null
                && currentUser.hasRole(UserRole.CUSTOMER)
                && !currentUser.hasRole(UserRole.ADMIN)
                && !currentUser.hasRole(UserRole.SALES_EMPLOYEE);
    }
}
