package de.fhdw.webshop.jobs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateJobLocationRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 255) String street,
        @NotBlank @Size(max = 20) String houseNumber,
        @NotBlank @Size(max = 10) String postalCode,
        @NotBlank @Size(max = 100) String city,
        @NotBlank @Size(max = 50) String locationType
) {}
