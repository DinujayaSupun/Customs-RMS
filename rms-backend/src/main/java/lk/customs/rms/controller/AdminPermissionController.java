package lk.customs.rms.controller;

import jakarta.validation.Valid;
import lk.customs.rms.dto.PermissionMatrixResponse;
import lk.customs.rms.dto.UpdatePermissionMatrixRequest;
import lk.customs.rms.security.CurrentUserService;
import lk.customs.rms.service.AuditLogService;
import lk.customs.rms.service.PermissionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@CrossOrigin
@RequestMapping("/api/admin/permissions")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPermissionController {

    private final PermissionService permissionService;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;

    public AdminPermissionController(PermissionService permissionService,
                                     CurrentUserService currentUserService,
                                     AuditLogService auditLogService) {
        this.permissionService = permissionService;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public PermissionMatrixResponse list() {
        return permissionService.getPermissionMatrix();
    }

    @PutMapping
    public PermissionMatrixResponse update(@Valid @RequestBody UpdatePermissionMatrixRequest request,
                                           Authentication authentication) {
        PermissionMatrixResponse updated = permissionService.updatePermissionMatrix(request);

        Long actorId = currentUserService.requireUser(authentication).getId();
        auditLogService.logEvent(
                "ROLE_PERMISSION",
                0L,
                "PERMISSION_UPDATE",
                actorId,
                "Admin updated permission matrix",
                "{\"entryCount\":" + request.getEntries().size() + "}"
        );

        return updated;
    }
}