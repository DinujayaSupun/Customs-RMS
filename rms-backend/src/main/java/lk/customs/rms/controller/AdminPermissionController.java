package lk.customs.rms.controller;

import jakarta.validation.Valid;
import lk.customs.rms.dto.DcAutoForwardConfigResponse;
import lk.customs.rms.dto.PermissionMatrixResponse;
import lk.customs.rms.dto.UpdateDcAutoForwardConfigRequest;
import lk.customs.rms.dto.UpdatePermissionMatrixRequest;
import lk.customs.rms.security.CurrentUserService;
import lk.customs.rms.service.AuditLogService;
import lk.customs.rms.service.DcAutoForwardConfigService;
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
    private final DcAutoForwardConfigService dcAutoForwardConfigService;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;

    public AdminPermissionController(PermissionService permissionService,
                                     DcAutoForwardConfigService dcAutoForwardConfigService,
                                     CurrentUserService currentUserService,
                                     AuditLogService auditLogService) {
        this.permissionService = permissionService;
        this.dcAutoForwardConfigService = dcAutoForwardConfigService;
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

    @GetMapping("/dc-auto-forward")
    public DcAutoForwardConfigResponse getDcAutoForwardConfig() {
        return dcAutoForwardConfigService.getConfig();
    }

    @PutMapping("/dc-auto-forward")
    public DcAutoForwardConfigResponse updateDcAutoForwardConfig(@Valid @RequestBody UpdateDcAutoForwardConfigRequest request,
                                                                 Authentication authentication) {
        DcAutoForwardConfigResponse updated = dcAutoForwardConfigService.updateConfig(request);

        Long actorId = currentUserService.requireUser(authentication).getId();
        auditLogService.logEvent(
                "ROLE_PERMISSION",
                0L,
                "DC_AUTO_FORWARD_CONFIG_UPDATE",
                actorId,
                "Admin updated DC auto-forward configuration",
                "{\"enabled\":" + updated.getEnabled()
                        + ",\"timeoutMinutes\":" + updated.getTimeoutMinutes()
                        + ",\"receiverUserId\":" + updated.getReceiverUserId() + "}"
        );

        return updated;
    }
}