package de.fhdw.webshop.returnrequest;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReturnRequestImageRepository extends JpaRepository<ReturnRequestImage, Long> {

    Optional<ReturnRequestImage> findByIdAndReturnRequestId(Long id, Long returnRequestId);

    Optional<ReturnRequestImage> findByIdAndReturnRequestIdAndReturnRequestCustomerId(
            Long id,
            Long returnRequestId,
            Long customerId);
}
