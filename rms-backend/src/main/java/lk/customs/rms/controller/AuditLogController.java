package lk.customs.rms.controller;

import lk.customs.rms.dto.AuditLogResponse;
import lk.customs.rms.enums.AppPermission;
import lk.customs.rms.exception.BadRequestException;
import lk.customs.rms.security.CurrentUserService;
import lk.customs.rms.service.AuditLogService;
import lk.customs.rms.service.PermissionService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/documents/{documentId}/audit-logs")
public class AuditLogController {

    private final AuditLogService auditLogService;
    private final CurrentUserService currentUserService;
    private final PermissionService permissionService;

    public AuditLogController(AuditLogService auditLogService,
                              CurrentUserService currentUserService,
                              PermissionService permissionService) {
        this.auditLogService = auditLogService;
        this.currentUserService = currentUserService;
        this.permissionService = permissionService;
    }

    @GetMapping
    public List<AuditLogResponse> getAuditHistory(@PathVariable Long documentId, Authentication authentication) {
        Long actorUserId = currentUserService.requireUserId(authentication);
        // Intentionally gated by VIEW_LOGS alone (a system-wide audit capability) rather than per-document
        // view access: holders of VIEW_LOGS are trusted to audit any document's activity trail.
        if (!permissionService.hasPermission(actorUserId, AppPermission.VIEW_LOGS)) {
            throw new BadRequestException("You are not allowed to view logs.");
        }
        return auditLogService.getHistoryForDocument(documentId);
    }
}
