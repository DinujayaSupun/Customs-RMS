package lk.customs.rms.service;

import lk.customs.rms.dto.PermissionMatrixResponse;
import lk.customs.rms.dto.UpdatePermissionMatrixRequest;
import lk.customs.rms.entity.User;
import lk.customs.rms.enums.AppPermission;

import java.util.List;

public interface PermissionService {
    boolean hasPermission(Long userId, AppPermission permission);
    boolean hasPermission(User user, AppPermission permission);
    void ensurePermission(Long userId, AppPermission permission, String message);
    List<String> permissionNamesForUser(User user);
    PermissionMatrixResponse getPermissionMatrix();
    PermissionMatrixResponse updatePermissionMatrix(UpdatePermissionMatrixRequest request);
}