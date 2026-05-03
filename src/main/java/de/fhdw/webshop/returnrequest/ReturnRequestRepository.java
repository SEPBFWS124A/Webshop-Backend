package de.fhdw.webshop.returnrequest;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, Long> {

    List<ReturnRequest> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
}
