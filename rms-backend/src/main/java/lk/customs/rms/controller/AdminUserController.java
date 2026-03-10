package lk.customs.rms.controller;

import jakarta.validation.Valid;
import lk.customs.rms.dto.*;
import lk.customs.rms.security.CurrentUserService;
import lk.customs.rms.service.AuditLogService;
import lk.customs.rms.service.AdminUserService;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@CrossOrigin
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;

    public AdminUserController(AdminUserService adminUserService,
                               CurrentUserService currentUserService,
                               AuditLogService auditLogService) {
        this.adminUserService = adminUserService;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public Page<AdminUserResponse> list(@RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size,
                                        @RequestParam(required = false) String search,
                                        @RequestParam(required = false) String role,
                                        @RequestParam(required = false) Boolean active) {
        return adminUserService.list(page, size, search, role, active);
    }

    @GetMapping("/roles")
    public List<String> roles() {
        return adminUserService.allowedRoles();
    }

    @GetMapping("/duplicates")
    public List<DuplicateUserCandidateResponse> duplicates() {
        return adminUserService.findDuplicateCandidates();
    }

    @PostMapping
    public AdminUserResponse create(@Valid @RequestBody AdminUserCreateRequest request,
                                    Authentication authentication) {
        AdminUserResponse created = adminUserService.create(request);
        Long actorId = currentUserService.requireUser(authentication).getId();
        auditLogService.logEvent(
                "USER",
                created.getId(),
                "USER_CREATE",
                actorId,
                "Admin created user " + created.getUsername(),
                null
        );
        return created;
    }

    @PutMapping("/{userId}")
    public AdminUserResponse update(@PathVariable Long userId,
                                    @Valid @RequestBody AdminUserUpdateRequest request,
                                    Authentication authentication) {
        AdminUserResponse updated = adminUserService.update(userId, request);
        Long actorId = currentUserService.requireUser(authentication).getId();
        auditLogService.logEvent(
                "USER",
                updated.getId(),
                "USER_UPDATE",
                actorId,
                "Admin updated user profile",
                null
        );
        return updated;
    }

    @PatchMapping("/{userId}/activate")
    public AdminUserResponse activate(@PathVariable Long userId,
                                      Authentication authentication) {
        AdminUserResponse activated = adminUserService.activate(userId);
        Long actorId = currentUserService.requireUser(authentication).getId();
        auditLogService.logEvent(
                "USER",
                activated.getId(),
                "USER_ACTIVATE",
                actorId,
                "Admin activated user",
                null
        );
        return activated;
    }

    @PatchMapping("/{userId}/deactivate")
    public AdminUserResponse deactivate(@PathVariable Long userId,
                                        @Valid @RequestBody DeactivateUserRequest request,
                                        Authentication authentication) {
        AdminUserResponse deactivated = adminUserService.deactivate(userId, request.getFallbackDcUserId());
        Long actorId = currentUserService.requireUser(authentication).getId();
        auditLogService.logEvent(
                "USER",
                deactivated.getId(),
                "USER_DEACTIVATE",
                actorId,
                "Admin deactivated user and transferred active ownership",
                "{\"fallbackDcUserId\":" + request.getFallbackDcUserId() + "}"
        );
        return deactivated;
    }

    @PatchMapping("/{userId}/reset-password")
    public void resetPassword(@PathVariable Long userId,
                              @Valid @RequestBody ResetPasswordRequest request,
                              Authentication authentication) {
        adminUserService.resetPassword(userId, request.getNewPassword());
        Long actorId = currentUserService.requireUser(authentication).getId();
        auditLogService.logEvent(
                "USER",
                userId,
                "USER_RESET_PASSWORD",
                actorId,
                "Admin reset user password",
                null
        );
    }

    @PostMapping("/merge")
    public void merge(@Valid @RequestBody MergeUsersRequest request,
                      Authentication authentication) {
        adminUserService.mergeUsers(request.getSourceUserId(), request.getTargetUserId());
        Long actorId = currentUserService.requireUser(authentication).getId();
        auditLogService.logEvent(
                "USER",
                request.getTargetUserId(),
                "USER_MERGE",
                actorId,
                "Admin merged source user into target user",
                "{\"sourceUserId\":" + request.getSourceUserId() + ",\"targetUserId\":" + request.getTargetUserId() + "}"
        );
    }

    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String search,
                                         @RequestParam(required = false) String role,
                                         @RequestParam(required = false) Boolean active) {
        String csv = adminUserService.exportCsv(search, role, active);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=users-export.csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }
}
