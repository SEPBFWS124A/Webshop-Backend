package de.fhdw.webshop.returnrequest;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReturnRequestItemRepository extends JpaRepository<ReturnRequestItem, Long> {

    boolean existsByOrderItemId(Long orderItemId);
}
