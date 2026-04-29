package de.fhdw.webshop.admin.statistics;

import de.fhdw.webshop.admin.statistics.alerting.StatisticAlertService;
import de.fhdw.webshop.admin.statistics.alerting.dto.StatisticAlertThresholdRequest;
import de.fhdw.webshop.admin.statistics.alerting.dto.StatisticAlertThresholdResponse;
import de.fhdw.webshop.admin.statistics.alerting.dto.StatisticAlertWarningResponse;
import de.fhdw.webshop.admin.statistics.dto.AdminStatisticsDashboardResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin/statistics")
@PreAuthorize("hasAnyRole('SALES_EMPLOYEE', 'ADMIN')")
@RequiredArgsConstructor
public class AdminStatisticsController {

    private final AdminStatisticsService adminStatisticsService;
    private final StatisticAlertService statisticAlertService;

    @GetMapping
    public ResponseEntity<AdminStatisticsDashboardResponse> getDashboard(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return ResponseEntity.ok(adminStatisticsService.getDashboard(from, to));
    }

    @GetMapping("/warnings")
    public ResponseEntity<List<StatisticAlertWarningResponse>> getWarnings(
            @RequestParam(defaultValue = "false") boolean includeDone) {
        return ResponseEntity.ok(statisticAlertService.evaluateAndListWarnings(includeDone));
    }

    @PatchMapping("/warnings/{id}/read")
    public ResponseEntity<StatisticAlertWarningResponse> markWarningAsRead(@PathVariable Long id) {
        return ResponseEntity.ok(statisticAlertService.markAsRead(id));
    }

    @PatchMapping("/warnings/{id}/done")
    public ResponseEntity<StatisticAlertWarningResponse> markWarningAsDone(@PathVariable Long id) {
        return ResponseEntity.ok(statisticAlertService.markAsDone(id));
    }

    @GetMapping("/thresholds")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<StatisticAlertThresholdResponse>> getThresholds() {
        return ResponseEntity.ok(statisticAlertService.getThresholds());
    }

    @PostMapping("/thresholds")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StatisticAlertThresholdResponse> createOrUpdateThreshold(
            @Valid @RequestBody StatisticAlertThresholdRequest request) {
        return ResponseEntity.ok(statisticAlertService.createOrUpdateThreshold(request));
    }

    @PutMapping("/thresholds/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StatisticAlertThresholdResponse> updateThreshold(
            @PathVariable Long id,
            @Valid @RequestBody StatisticAlertThresholdRequest request) {
        return ResponseEntity.ok(statisticAlertService.updateThreshold(id, request));
    }
}
