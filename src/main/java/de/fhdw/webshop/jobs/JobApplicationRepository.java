package de.fhdw.webshop.jobs;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {
    List<JobApplication> findAllByOrderByCreatedAtDesc();
    List<JobApplication> findByJobPostingIdOrderByCreatedAtDesc(Long jobPostingId);
    List<JobApplication> findByStatusOrderByCreatedAtDesc(String status);
    List<JobApplication> findByJobPostingIdAndStatusOrderByCreatedAtDesc(Long jobPostingId, String status);
}
