package lk.customs.rms.service.impl;

import lk.customs.rms.dto.PermissionMatrixResponse;
import lk.customs.rms.dto.RolePermissionEntryResponse;
import lk.customs.rms.dto.UpdatePermissionMatrixRequest;
import lk.customs.rms.entity.Role;
import lk.customs.rms.entity.RolePermission;
import lk.customs.rms.entity.User;
import lk.customs.rms.enums.AppPermission;
import lk.customs.rms.exception.BadRequestException;
import lk.customs.rms.repository.RolePermissionRepository;
import lk.customs.rms.repository.RoleRepository;
import lk.customs.rms.repository.UserRepository;
import lk.customs.rms.service.PermissionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

@Service
public class PermissionServiceImpl implements PermissionService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;

    public PermissionServiceImpl(UserRepository userRepository,
                                 RoleRepository roleRepository,
                                 RolePermissionRepository rolePermissionRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
    }

    @Override
    public boolean hasPermission(Long userId, AppPermission permission) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found: " + userId));
        return hasPermission(user, permission);
    }

    @Override
    public boolean hasPermission(User user, AppPermission permission) {
        if (user == null || user.getRole() == null || permission == null) return false;

        return rolePermissionRepository
                .findByRole_RoleNameIgnoreCaseOrderByPermissionNameAsc(user.getRole().getRoleName())
                .stream()
                .anyMatch(rp -> Boolean.TRUE.equals(rp.getEnabled())
                        && permission.name().equalsIgnoreCase(rp.getPermissionName()));
    }

    @Override
    public void ensurePermission(Long userId, AppPermission permission, String message) {
        if (!hasPermission(userId, permission)) {
            throw new BadRequestException(message);
        }
    }

    @Override
    public List<String> permissionNamesForUser(User user) {
        if (user == null || user.getRole() == null) return List.of();

        return rolePermissionRepository
                .findByRole_RoleNameIgnoreCaseOrderByPermissionNameAsc(user.getRole().getRoleName())
                .stream()
                .filter(rp -> Boolean.TRUE.equals(rp.getEnabled()))
                .map(RolePermission::getPermissionName)
                .map(name -> name.toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    @Override
    public PermissionMatrixResponse getPermissionMatrix() {
        return buildMatrix();
    }

    @Override
    @Transactional
    public PermissionMatrixResponse updatePermissionMatrix(UpdatePermissionMatrixRequest request) {
        for (UpdatePermissionMatrixRequest.PermissionEntry entry : request.getEntries()) {
            String roleName = normalizeRole(entry.getRoleName());
            String permissionName = normalizePermission(entry.getPermission());

            Role role = roleRepository.findByRoleName(roleName)
                    .orElseThrow(() -> new BadRequestException("Role not found: " + roleName));

            RolePermission rp = rolePermissionRepository
                    .findByRole_IdAndPermissionNameIgnoreCase(role.getId(), permissionName)
                    .orElseGet(() -> {
                        RolePermission created = new RolePermission();
                        created.setRole(role);
                        created.setPermissionName(permissionName);
                        return created;
                    });

            rp.setEnabled(Boolean.TRUE.equals(entry.getEnabled()));
            rolePermissionRepository.save(rp);
        }

        return buildMatrix();
    }

    private PermissionMatrixResponse buildMatrix() {
        List<String> roles = roleRepository.findAll().stream()
                .map(Role::getRoleName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();

        List<String> permissions = Arrays.stream(AppPermission.values())
                .map(Enum::name)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();

        Set<String> expected = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String role : roles) {
            for (String permission : permissions) {
                expected.add(role + "::" + permission);
            }
        }

        List<RolePermissionEntryResponse> savedEntries = rolePermissionRepository.findAllByOrderByRole_RoleNameAscPermissionNameAsc()
                .stream()
                .map(rp -> RolePermissionEntryResponse.builder()
                        .roleName(rp.getRole().getRoleName())
                        .permission(normalizePermission(rp.getPermissionName()))
                        .enabled(Boolean.TRUE.equals(rp.getEnabled()))
                        .build())
                .toList();

        savedEntries.forEach(entry -> expected.remove(entry.getRoleName() + "::" + entry.getPermission()));

        List<RolePermissionEntryResponse> combinedEntries = new java.util.ArrayList<>(savedEntries);
        for (String missing : expected) {
            String[] parts = missing.split("::", 2);
            combinedEntries.add(RolePermissionEntryResponse.builder()
                    .roleName(parts[0])
                    .permission(parts[1])
                    .enabled(false)
                    .build());
        }

        combinedEntries.sort(Comparator
                .comparing(RolePermissionEntryResponse::getPermission, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(RolePermissionEntryResponse::getRoleName, String.CASE_INSENSITIVE_ORDER));

        return PermissionMatrixResponse.builder()
                .roles(roles)
                .permissions(permissions)
                .entries(combinedEntries)
                .build();
    }

    private String normalizeRole(String roleName) {
        String value = roleName == null ? "" : roleName.trim().toUpperCase(Locale.ROOT);
        if (value.isEmpty()) throw new BadRequestException("Role name is required.");
        return value;
    }

    private String normalizePermission(String permission) {
        String value = permission == null ? "" : permission.trim().toUpperCase(Locale.ROOT);
        if (value.isEmpty()) throw new BadRequestException("Permission name is required.");

        try {
            return AppPermission.valueOf(value).name();
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Unknown permission: " + permission);
        }
    }
}