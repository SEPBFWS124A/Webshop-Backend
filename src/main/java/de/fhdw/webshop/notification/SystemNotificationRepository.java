package de.fhdw.webshop.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SystemNotificationRepository extends JpaRepository<SystemNotification, Long> {

    List<SystemNotification> findAllByOrderByCreatedAtDesc();

    long countByReadFalse();

    @Modifying
    @Query("UPDATE SystemNotification n SET n.read = true WHERE n.read = false")
    void markAllAsRead();
}
