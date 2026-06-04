package de.fhdw.webshop.jobs;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobApplicationFileRepository extends JpaRepository<JobApplicationFile, Long> {
    List<JobApplicationFile> findByApplicationIdOrderByCreatedAtAsc(Long applicationId);
}
