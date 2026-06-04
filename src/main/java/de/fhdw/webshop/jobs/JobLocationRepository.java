package de.fhdw.webshop.jobs;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobLocationRepository extends JpaRepository<JobLocation, Long> {
    List<JobLocation> findAllByOrderByNameAsc();
}
