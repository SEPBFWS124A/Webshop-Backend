package de.fhdw.webshop.loyalty;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LuckyWheelPrizeRepository extends JpaRepository<LuckyWheelPrize, Long> {

    List<LuckyWheelPrize> findByActiveTrueOrderByProbabilityDesc();
}
