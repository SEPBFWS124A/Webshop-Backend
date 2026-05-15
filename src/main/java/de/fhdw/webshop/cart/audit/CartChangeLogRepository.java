package de.fhdw.webshop.cart.audit;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartChangeLogRepository extends JpaRepository<CartChangeLog, Long> {

    List<CartChangeLog> findTop30ByCustomerIdOrderByCreatedAtDesc(Long customerId);
}
