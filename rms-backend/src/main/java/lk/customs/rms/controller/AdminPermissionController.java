package lk.customs.rms.controller;

import jakarta.validation.Valid;
import lk.customs.rms.dto.DcAutoForwardConfigResponse;
import lk.customs.rms.dto.PermissionMatrixResponse;
import lk.customs.rms.dto.PermissionsPageSaveRequest;
import lk.customs.rms.dto.PermissionsPageSaveResponse;
import lk.customs.rms.dto.UpdateDcAutoForwardConfigRequest;
import lk.customs.rms.dto.UpdatePermissionMatrixRequest;
import lk.customs.rms.security.CurrentUserService;
import lk.customs.rms.service.AuditLogService;
import lk.customs.rms.service.DcAutoForwardConfigService;
import lk.customs.rms.service.PermissionService;
import lk.customs.rms.service.RealtimeNotificationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController

@RequestMapping("/api/admin/permissions")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPermissionController {

    private final PermissionService permissionService;
    private final DcAutoForwardConfigService dcAutoForwardConfigService;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;
    private final RealtimeNotificationService realtimeNotificationService;

    public AdminPermissionController(PermissionService permissionService,
                                     DcAutoForwardConfigService dcAutoForwardConfigService,
                                     CurrentUserService currentUserService,
                                     AuditLogService auditLogService,
                                     RealtimeNotificationService realtimeNotificationService) {
        this.permissionService = permissionService;
        this.dcAutoForwardConfigService = dcAutoForwardConfigService;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
        this.realtimeNotificationService = realtimeNotificationService;
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
        auditLogService.logEventWithDetails(
                "ROLE_PERMISSION",
                0L,
                "PERMISSION_UPDATE",
                actorId,
                "Admin updated permission matrix",
                Map.of("entryCount", request.getEntries().size())
        );

        realtimeNotificationService.notifyPermissionsUpdated();

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
        auditLogService.logEventWithDetails(
                "ROLE_PERMISSION",
                0L,
                "DC_AUTO_FORWARD_CONFIG_UPDATE",
                actorId,
                "Admin updated permission workflow configuration",
                dcAutoForwardConfigDetails(updated)
        );

        return updated;
    }

    @PutMapping("/page")
    @Transactional
    public PermissionsPageSaveResponse savePermissionsPage(@Valid @RequestBody PermissionsPageSaveRequest request,
                                                           Authentication authentication) {
        PermissionMatrixResponse updatedMatrix = permissionService.updatePermissionMatrix(request.getPermissionMatrix());
        DcAutoForwardConfigResponse updatedConfig = dcAutoForwardConfigService.updateConfig(request.getDcAutoForwardConfig());

        Long actorId = currentUserService.requireUser(authentication).getId();
        auditLogService.logEventWithDetails(
                "ROLE_PERMISSION",
                0L,
                "PERMISSION_PAGE_UPDATE",
                actorId,
                "Admin updated permission page settings",
                dcAutoForwardConfigDetails(updatedConfig, request.getPermissionMatrix().getEntries().size())
        );

        realtimeNotificationService.notifyPermissionsUpdated();

        return PermissionsPageSaveResponse.builder()
                .permissionMatrix(updatedMatrix)
                .dcAutoForwardConfig(updatedConfig)
                .build();
    }

    private Map<String, Object> dcAutoForwardConfigDetails(DcAutoForwardConfigResponse config) {
        return dcAutoForwardConfigDetails(config, null);
    }

    private Map<String, Object> dcAutoForwardConfigDetails(DcAutoForwardConfigResponse config, Integer entryCount) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (entryCount != null) details.put("entryCount", entryCount);
        details.put("enabled", config.getEnabled());
        details.put("timeoutMinutes", config.getTimeoutMinutes());
        details.put("receiverUserId", config.getReceiverUserId());
        details.put("forwardReturnAllowedStatuses", config.getForwardReturnAllowedStatuses());
        details.put("approveRejectButtonsEnabled", config.getApproveRejectButtonsEnabled());
        return details;
    }
}
