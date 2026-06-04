package de.fhdw.webshop.jobs;

import de.fhdw.webshop.address.AddressLookupService;
import de.fhdw.webshop.address.GeocodedAddressResponse;
import de.fhdw.webshop.admin.AuditInitiator;
import de.fhdw.webshop.admin.AuditLogService;
import de.fhdw.webshop.jobs.dto.CreateJobLocationRequest;
import de.fhdw.webshop.jobs.dto.JobLocationResponse;
import de.fhdw.webshop.user.User;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobLocationService {

    private static final Set<String> VALID_TYPES = Set.of("FILIALE", "LAGER", "VERWALTUNG");

    private final JobLocationRepository locationRepository;
    private final AuditLogService auditLogService;
    private final AddressLookupService addressLookupService;

    @Transactional(readOnly = true)
    public List<JobLocationResponse> getAll() {
        return locationRepository.findAllByOrderByNameAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public JobLocationResponse create(CreateJobLocationRequest request, User admin) {
        validate(request);
        JobLocation location = new JobLocation();
        applyRequest(location, request);
        geocode(location);
        JobLocation saved = locationRepository.save(location);
        auditLogService.record(admin, "CREATE_JOB_LOCATION", "JobLocation", saved.getId(),
                AuditInitiator.ADMIN, "Standort erstellt: " + saved.getName());
        return toResponse(saved);
    }

    @Transactional
    public JobLocationResponse update(Long id, CreateJobLocationRequest request, User admin) {
        JobLocation location = locationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Standort nicht gefunden: " + id));
        validate(request);
        applyRequest(location, request);
        geocode(location);
        JobLocation saved = locationRepository.save(location);
        auditLogService.record(admin, "UPDATE_JOB_LOCATION", "JobLocation", saved.getId(),
                AuditInitiator.ADMIN, "Standort aktualisiert: " + saved.getName());
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long id, User admin) {
        JobLocation location = locationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Standort nicht gefunden: " + id));
        String name = location.getName();
        locationRepository.delete(location);
        auditLogService.record(admin, "DELETE_JOB_LOCATION", "JobLocation", id,
                AuditInitiator.ADMIN, "Standort gelöscht: " + name);
    }

    private void geocode(JobLocation location) {
        try {
            String street = location.getStreet() + " " + location.getHouseNumber();
            Optional<GeocodedAddressResponse> result = addressLookupService.geocodeAddress(
                    street, location.getPostalCode(), location.getCity(), "Deutschland");
            if (result.isPresent()) {
                location.setLatitude(result.get().latitude());
                location.setLongitude(result.get().longitude());
            } else {
                location.setLatitude(null);
                location.setLongitude(null);
                log.warn("Geokodierung für Standort '{}' lieferte kein Ergebnis.", location.getName());
            }
        } catch (Exception e) {
            location.setLatitude(null);
            location.setLongitude(null);
            log.error("Geokodierung fehlgeschlagen für '{}': {}", location.getName(), e.getMessage());
        }
    }

    private void validate(CreateJobLocationRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name darf nicht leer sein.");
        }
        if (request.street() == null || request.street().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Straße darf nicht leer sein.");
        }
        if (request.houseNumber() == null || request.houseNumber().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hausnummer darf nicht leer sein.");
        }
        if (request.postalCode() == null || request.postalCode().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PLZ darf nicht leer sein.");
        }
        if (request.city() == null || request.city().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ort darf nicht leer sein.");
        }
        String type = request.locationType() != null ? request.locationType().toUpperCase() : "";
        if (!VALID_TYPES.contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ungültiger Standorttyp. Erlaubt: FILIALE, LAGER, VERWALTUNG");
        }
    }

    private void applyRequest(JobLocation location, CreateJobLocationRequest request) {
        location.setName(request.name().trim());
        location.setStreet(request.street().trim());
        location.setHouseNumber(request.houseNumber().trim());
        location.setPostalCode(request.postalCode().trim());
        location.setCity(request.city().trim());
        location.setLocationType(request.locationType().toUpperCase());
    }

    public JobLocationResponse toResponse(JobLocation location) {
        return new JobLocationResponse(
                location.getId(),
                location.getName(),
                location.getStreet(),
                location.getHouseNumber(),
                location.getPostalCode(),
                location.getCity(),
                location.getLocationType(),
                location.getLatitude(),
                location.getLongitude(),
                location.getCreatedAt(),
                location.getUpdatedAt());
    }
}
