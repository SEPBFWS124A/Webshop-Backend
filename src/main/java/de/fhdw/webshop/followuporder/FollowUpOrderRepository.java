package de.fhdw.webshop.followuporder;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FollowUpOrderRepository extends JpaRepository<FollowUpOrder, Long> {

    List<FollowUpOrder> findByCustomerIdOrderByExecutionDateAsc(Long customerId);

    Optional<FollowUpOrder> findByIdAndCustomerId(Long id, Long customerId);

    List<FollowUpOrder> findByStatusAndExecutionDateLessThanEqual(FollowUpOrderStatus status, LocalDate date);
}
