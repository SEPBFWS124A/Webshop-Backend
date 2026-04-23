package de.fhdw.webshop.loyalty;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LuckyWheelSpinRepository extends JpaRepository<LuckyWheelSpin, Long> {

    Optional<LuckyWheelSpin> findFirstByUserIdOrderBySpunAtDesc(Long userId);
}
