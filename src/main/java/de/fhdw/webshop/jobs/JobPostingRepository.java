package de.fhdw.webshop.jobs;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {
    List<JobPosting> findAllByOrderByDisplayOrderAsc();
    List<JobPosting> findByStatusOrderByDisplayOrderAsc(String status);
    List<JobPosting> findByStatusAndEmploymentTypeOrderByDisplayOrderAsc(String status, String employmentType);
    List<JobPosting> findByStatusAndJobLocation_CityOrderByDisplayOrderAsc(String status, String city);
    List<JobPosting> findByStatusAndEmploymentTypeAndJobLocation_CityOrderByDisplayOrderAsc(String status, String employmentType, String city);
}
