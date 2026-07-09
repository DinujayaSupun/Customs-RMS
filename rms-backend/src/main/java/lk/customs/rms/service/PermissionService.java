package lk.customs.rms.service;

import lk.customs.rms.dto.PermissionMatrixResponse;
import lk.customs.rms.dto.UpdatePermissionMatrixRequest;
import lk.customs.rms.dto.UpdateUserPermissionsRequest;
import lk.customs.rms.dto.UserPermissionsResponse;
import lk.customs.rms.entity.User;
import lk.customs.rms.enums.AppPermission;

import java.util.List;
import java.util.Set;

public interface PermissionService {
    boolean hasPermission(Long userId, AppPermission permission);
    boolean hasPermission(User user, AppPermission permission);
    void ensurePermission(Long userId, AppPermission permission, String message);
    List<String> permissionNamesForUser(User user);
    Set<AppPermission> getPermissionsForUser(Long userId);
    PermissionMatrixResponse getPermissionMatrix();
    PermissionMatrixResponse updatePermissionMatrix(UpdatePermissionMatrixRequest request);
    UserPermissionsResponse getUserPermissions(Long userId);
    UserPermissionsResponse updateUserPermissions(Long userId, UpdateUserPermissionsRequest request);
}