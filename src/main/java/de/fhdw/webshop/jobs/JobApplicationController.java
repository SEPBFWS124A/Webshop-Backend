package de.fhdw.webshop.jobs;

import de.fhdw.webshop.jobs.dto.JobApplicationRequest;
import de.fhdw.webshop.jobs.dto.JobApplicationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class JobApplicationController {

    private final JobApplicationService jobApplicationService;

    @PostMapping(value = "/api/jobs/{id}/apply", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<JobApplicationResponse> apply(
            @PathVariable Long id,
            @RequestParam String applicantName,
            @RequestParam String applicantEmail,
            @RequestParam(required = false) String applicantPhone,
            @RequestParam(required = false) String motivationText,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        JobApplicationRequest request = new JobApplicationRequest(
                applicantName, applicantEmail, applicantPhone, motivationText);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(jobApplicationService.submitApplication(id, request, files));
    }
}
