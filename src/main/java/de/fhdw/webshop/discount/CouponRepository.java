package de.fhdw.webshop.discount;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    List<Coupon> findByCustomerId(Long customerId);

    Optional<Coupon> findByCode(String code);

    Optional<Coupon> findByCodeAndCustomerId(String code, Long customerId);
}
