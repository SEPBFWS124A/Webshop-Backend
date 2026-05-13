package de.fhdw.webshop.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SystemNotificationRepository extends JpaRepository<SystemNotification, Long> {

    List<SystemNotification> findAllByOrderByCreatedAtDesc();

    List<SystemNotification> findByRecipientUserIsNullOrderByCreatedAtDesc();

    long countByReadFalse();

    long countByRecipientUserIsNullAndReadFalse();

    List<SystemNotification> findByRecipientUserIdOrderByCreatedAtDesc(Long recipientUserId);

    long countByRecipientUserIdAndReadFalse(Long recipientUserId);

    @Modifying
    @Query("UPDATE SystemNotification n SET n.read = true WHERE n.recipientUser IS NULL AND n.read = false")
    void markAllAsRead();

    @Modifying
    @Query("UPDATE SystemNotification n SET n.read = true WHERE n.recipientUser.id = :recipientUserId AND n.read = false")
    void markAllAsReadForRecipient(@Param("recipientUserId") Long recipientUserId);
}
