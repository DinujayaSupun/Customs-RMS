package lk.customs.rms.controller;

import lk.customs.rms.dto.AuditLogFilterOptionsResponse;
import lk.customs.rms.dto.AuditLogResponse;
import lk.customs.rms.enums.AppPermission;
import lk.customs.rms.exception.BadRequestException;
import lk.customs.rms.security.CurrentUserService;
import lk.customs.rms.service.AuditLogService;
import lk.customs.rms.service.PermissionService;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/audit-logs")
public class LogsController {

    private final AuditLogService auditLogService;
    private final CurrentUserService currentUserService;
    private final PermissionService permissionService;

    public LogsController(AuditLogService auditLogService,
                          CurrentUserService currentUserService,
                          PermissionService permissionService) {
        this.auditLogService = auditLogService;
        this.currentUserService = currentUserService;
        this.permissionService = permissionService;
    }

    @GetMapping
    public Page<AuditLogResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) Long performedByUserId,
            @RequestParam(required = false) String document,
            Authentication authentication
    ) {
        ensureCanViewLogs(authentication);
        return auditLogService.searchLogs(fromDate, toDate, actionType, performedByUserId, document, page, size);
    }

    @GetMapping("/filter-options")
    public AuditLogFilterOptionsResponse filterOptions(Authentication authentication) {
        ensureCanViewLogs(authentication);
        return auditLogService.getFilterOptions();
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) Long performedByUserId,
            @RequestParam(required = false) String document,
            Authentication authentication
    ) {
        ensureCanViewLogs(authentication);
        byte[] bytes = auditLogService.exportCsvBytes(fromDate, toDate, actionType, performedByUserId, document);
        String fileName = "audit-logs-" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(bytes);
    }

    private void ensureCanViewLogs(Authentication authentication) {
        Long actorUserId = currentUserService.requireUserId(authentication);
        if (!permissionService.hasPermission(actorUserId, AppPermission.VIEW_LOGS)) {
            throw new BadRequestException("You are not allowed to view logs.");
        }
    }
}
