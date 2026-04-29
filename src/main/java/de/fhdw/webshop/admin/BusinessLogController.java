package de.fhdw.webshop.admin;

import de.fhdw.webshop.admin.dto.BusinessLogEntryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin/business-log")
@PreAuthorize("hasAnyRole('EMPLOYEE', 'SALES_EMPLOYEE', 'WAREHOUSE_EMPLOYEE', 'ADMIN')")
@RequiredArgsConstructor
public class BusinessLogController {

    private final BusinessLogService businessLogService;

    @GetMapping
    public ResponseEntity<List<BusinessLogEntryResponse>> getBusinessLog(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, name = "user") String userFilter,
            @RequestParam(required = false, name = "action") String actionFilter) {
        return ResponseEntity.ok(businessLogService.search(from, to, userFilter, actionFilter));
    }
}
