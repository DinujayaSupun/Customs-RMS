package lk.customs.rms.controller;

import jakarta.validation.Valid;
import lk.customs.rms.dto.*;
import lk.customs.rms.service.AdminUserService;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@CrossOrigin
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
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
    public AdminUserResponse create(@Valid @RequestBody AdminUserCreateRequest request) {
        return adminUserService.create(request);
    }

    @PutMapping("/{userId}")
    public AdminUserResponse update(@PathVariable Long userId,
                                    @Valid @RequestBody AdminUserUpdateRequest request) {
        return adminUserService.update(userId, request);
    }

    @PatchMapping("/{userId}/activate")
    public AdminUserResponse activate(@PathVariable Long userId) {
        return adminUserService.activate(userId);
    }

    @PatchMapping("/{userId}/deactivate")
    public AdminUserResponse deactivate(@PathVariable Long userId,
                                        @Valid @RequestBody DeactivateUserRequest request) {
        return adminUserService.deactivate(userId, request.getFallbackDcUserId());
    }

    @PatchMapping("/{userId}/reset-password")
    public void resetPassword(@PathVariable Long userId,
                              @Valid @RequestBody ResetPasswordRequest request) {
        adminUserService.resetPassword(userId, request.getNewPassword());
    }

    @PostMapping("/merge")
    public void merge(@Valid @RequestBody MergeUsersRequest request) {
        adminUserService.mergeUsers(request.getSourceUserId(), request.getTargetUserId());
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
