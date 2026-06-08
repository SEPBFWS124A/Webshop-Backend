package de.fhdw.webshop.jobs.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JobApplicationRequest(
        @NotBlank @Size(max = 255) String applicantName,
        @NotBlank @Email @Size(max = 255) String applicantEmail,
        @Size(max = 50) String applicantPhone,
        String motivationText
) {}
