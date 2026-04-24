package de.fhdw.webshop.standingorder;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface StandingOrderRepository extends JpaRepository<StandingOrder, Long> {

    List<StandingOrder> findByCustomerId(Long customerId);

    Optional<StandingOrder> findByIdAndCustomerId(Long standingOrderId, Long customerId);

    /** Used by the scheduler to find all active standing orders due today or overdue. */
    List<StandingOrder> findByActiveIsTrueAndNextExecutionDateLessThanEqual(LocalDate date);

    List<StandingOrder> findByActiveIsTrue();
}
