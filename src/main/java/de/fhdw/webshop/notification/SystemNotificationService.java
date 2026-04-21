package de.fhdw.webshop.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SystemNotificationService {

    private final SystemNotificationRepository repository;

    public List<SystemNotificationResponse> getAll() {
        return repository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(SystemNotificationResponse::from)
                .toList();
    }

    public long getUnreadCount() {
        return repository.countByReadFalse();
    }

    @Transactional
    public SystemNotificationResponse markAsRead(Long id) {
        SystemNotification notification = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Benachrichtigung nicht gefunden: " + id));
        notification.setRead(true);
        return SystemNotificationResponse.from(repository.save(notification));
    }

    @Transactional
    public void markAllAsRead() {
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
}
