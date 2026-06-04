package de.fhdw.webshop.aboutus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AboutUsImageRepository extends JpaRepository<AboutUsImage, Long> {
    List<AboutUsImage> findBySectionIdOrderByCreatedAtAsc(Long sectionId);
}
