package de.fhdw.webshop.advertisement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface AdvertisementRepository extends JpaRepository<Advertisement, Long> {
    List<Advertisement> findAllByOrderByActiveDescCreatedAtDescIdDesc();
    List<Advertisement> findByActiveTrueAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateDescCreatedAtDescIdDesc(
            LocalDate startsOnOrBefore,
            LocalDate endsOnOrAfter
    );
}
