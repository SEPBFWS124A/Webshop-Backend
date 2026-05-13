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
