package lk.customs.rms.service;

import lk.customs.rms.dto.AdminUserCreateRequest;
import lk.customs.rms.dto.AdminUserResponse;
import lk.customs.rms.dto.AdminUserUpdateRequest;
import lk.customs.rms.dto.DuplicateUserCandidateResponse;
import org.springframework.data.domain.Page;

import java.util.List;

public interface AdminUserService {
    Page<AdminUserResponse> list(int page, int size, String search, String role, Boolean active);
    List<String> allowedRoles();
    AdminUserResponse create(AdminUserCreateRequest request);
    AdminUserResponse update(Long userId, AdminUserUpdateRequest request);
    AdminUserResponse activate(Long userId);
    AdminUserResponse deactivate(Long userId, Long fallbackDcUserId);
    void resetPassword(Long userId, String newPassword);
    String exportCsv(String search, String role, Boolean active);
    List<DuplicateUserCandidateResponse> findDuplicateCandidates();
    void mergeUsers(Long sourceUserId, Long targetUserId);
}
