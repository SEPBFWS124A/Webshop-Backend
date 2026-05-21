package de.fhdw.webshop.reservation;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AvailabilityNotificationRepository extends JpaRepository<AvailabilityNotification, Long> {

    Optional<AvailabilityNotification> findByUserIdAndProductId(Long userId, Long productId);

    List<AvailabilityNotification> findByActiveTrue();
}
