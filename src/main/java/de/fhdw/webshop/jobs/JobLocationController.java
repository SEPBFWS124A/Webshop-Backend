package de.fhdw.webshop.jobs;

import de.fhdw.webshop.jobs.dto.JobLocationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class JobLocationController {

    private final JobLocationService jobLocationService;

    @GetMapping("/api/job-locations")
    public ResponseEntity<List<JobLocationResponse>> getAll() {
        return ResponseEntity.ok(jobLocationService.getAll());
    }
}
