package de.fhdw.webshop.returnrequest;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, Long> {

    List<ReturnRequest> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    Optional<ReturnRequest> findByIdAndCustomerId(Long id, Long customerId);
}
