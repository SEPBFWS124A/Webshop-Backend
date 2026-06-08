package de.fhdw.webshop.jobs;

import de.fhdw.webshop.jobs.dto.JobPostingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class JobPostingController {

    private final JobPostingService jobPostingService;

    @GetMapping("/api/jobs")
    public ResponseEntity<List<JobPostingResponse>> getActivePostings(
            @RequestParam(required = false) String employmentType,
            @RequestParam(required = false) String location) {
        return ResponseEntity.ok(jobPostingService.getPublicPostings(employmentType, location));
    }

    @GetMapping("/api/jobs/{id}")
    public ResponseEntity<JobPostingResponse> getPosting(@PathVariable Long id) {
        return ResponseEntity.ok(jobPostingService.getPublicPosting(id));
    }
}
