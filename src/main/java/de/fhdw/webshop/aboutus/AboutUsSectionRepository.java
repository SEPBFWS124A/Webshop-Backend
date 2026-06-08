package de.fhdw.webshop.aboutus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AboutUsSectionRepository extends JpaRepository<AboutUsSection, Long> {
    List<AboutUsSection> findAllByOrderByDisplayOrderAsc();
}
