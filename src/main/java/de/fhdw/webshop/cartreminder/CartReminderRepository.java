package de.fhdw.webshop.cartreminder;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CartReminderRepository extends JpaRepository<CartReminder, Long> {

    Optional<CartReminder> findByUserId(Long userId);

    @Query("""
            SELECT reminder
            FROM CartReminder reminder
            JOIN FETCH reminder.user user
            WHERE reminder.lastModifiedAt IS NOT NULL
              AND reminder.lastModifiedAt <= :cutoff
              AND reminder.reminderSentAt IS NULL
              AND user.active = true
              AND user.cartReminderEnabled = true
            """)
    List<CartReminder> findDueReminders(@Param("cutoff") Instant cutoff);
}
