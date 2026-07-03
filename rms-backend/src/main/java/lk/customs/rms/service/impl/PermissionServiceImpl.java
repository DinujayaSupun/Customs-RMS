package lk.customs.rms.service.impl;

import lk.customs.rms.dto.PermissionMatrixResponse;
import lk.customs.rms.dto.RolePermissionEntryResponse;
import lk.customs.rms.dto.UpdatePermissionMatrixRequest;
import lk.customs.rms.dto.UpdateUserPermissionsRequest;
import lk.customs.rms.dto.UserPermissionsResponse;
import lk.customs.rms.entity.Role;
import lk.customs.rms.entity.RolePermission;
import lk.customs.rms.entity.User;
import lk.customs.rms.entity.UserPermission;
import lk.customs.rms.enums.AppPermission;
import lk.customs.rms.exception.BadRequestException;
import lk.customs.rms.repository.RolePermissionRepository;
import lk.customs.rms.repository.RoleRepository;
import lk.customs.rms.repository.UserPermissionRepository;
import lk.customs.rms.repository.UserRepository;
import lk.customs.rms.service.PermissionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Service
public class PermissionServiceImpl implements PermissionService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserPermissionRepository userPermissionRepository;

    public PermissionServiceImpl(UserRepository userRepository,
                                 RoleRepository roleRepository,
                                 RolePermissionRepository rolePermissionRepository,
                                 UserPermissionRepository userPermissionRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.userPermissionRepository = userPermissionRepository;
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

        // A per-user override, if present, wins over the role default (grant OR revoke).
        var override = userPermissionRepository
                .findByUserIdAndPermissionNameIgnoreCase(user.getId(), permission.name());
        if (override.isPresent()) {
            return Boolean.TRUE.equals(override.get().getEnabled());
        }

        return roleHasPermission(user.getRole().getRoleName(), permission.name());
    }

    private boolean roleHasPermission(String roleName, String permissionName) {
        return rolePermissionRepository
                .findByRole_RoleNameIgnoreCaseOrderByPermissionNameAsc(roleName)
                .stream()
                .anyMatch(rp -> Boolean.TRUE.equals(rp.getEnabled())
                        && permissionName.equalsIgnoreCase(rp.getPermissionName()));
    }

    /**
     * The user's effective enabled permission names (uppercase): role defaults with per-user
     * overrides applied (a grant adds a permission, a revoke removes one). No override = inherit.
     */
    private Set<String> effectivePermissionNames(User user) {
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        if (user == null || user.getRole() == null) return names;

        rolePermissionRepository
                .findByRole_RoleNameIgnoreCaseOrderByPermissionNameAsc(user.getRole().getRoleName())
                .stream()
                .filter(rp -> Boolean.TRUE.equals(rp.getEnabled()))
                .forEach(rp -> names.add(rp.getPermissionName().toUpperCase(Locale.ROOT)));

        for (UserPermission up : userPermissionRepository.findByUserId(user.getId())) {
            String name = up.getPermissionName().toUpperCase(Locale.ROOT);
            if (Boolean.TRUE.equals(up.getEnabled())) {
                names.add(name);
            } else {
                names.remove(name);
            }
        }
        return names;
    }

    @Override
    public void ensurePermission(Long userId, AppPermission permission, String message) {
        if (!hasPermission(userId, permission)) {
            throw new BadRequestException(message);
        }
    }

    @Override
    public List<String> permissionNamesForUser(User user) {
        return List.copyOf(effectivePermissionNames(user));
    }

    @Override
    public Set<AppPermission> getPermissionsForUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found: " + userId));
        Set<AppPermission> result = EnumSet.noneOf(AppPermission.class);
        for (String name : effectivePermissionNames(user)) {
            try {
                result.add(AppPermission.valueOf(name.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {}
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
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

    @Override
    @Transactional(readOnly = true)
    public UserPermissionsResponse getUserPermissions(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found: " + userId));
        return buildUserPermissions(user);
    }

    @Override
    @Transactional
    public UserPermissionsResponse updateUserPermissions(Long userId, UpdateUserPermissionsRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found: " + userId));

        for (UpdateUserPermissionsRequest.PermissionEntry entry : request.getEntries()) {
            String permissionName = normalizePermission(entry.getPermission());
            var existing = userPermissionRepository.findByUserIdAndPermissionNameIgnoreCase(userId, permissionName);

            if (entry.getOverride() == null) {
                // Inherit role: drop any override row for this permission.
                existing.ifPresent(userPermissionRepository::delete);
            } else {
                UserPermission up = existing.orElseGet(() -> {
                    UserPermission created = new UserPermission();
                    created.setUserId(userId);
                    created.setPermissionName(permissionName);
                    return created;
                });
                up.setEnabled(entry.getOverride());
                userPermissionRepository.save(up);
            }
        }

        return buildUserPermissions(user);
    }

    private UserPermissionsResponse buildUserPermissions(User user) {
        String roleName = user.getRole() == null ? null : user.getRole().getRoleName();

        Set<String> roleEnabled = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        if (roleName != null) {
            rolePermissionRepository.findByRole_RoleNameIgnoreCaseOrderByPermissionNameAsc(roleName)
                    .stream()
                    .filter(rp -> Boolean.TRUE.equals(rp.getEnabled()))
                    .forEach(rp -> roleEnabled.add(rp.getPermissionName().toUpperCase(Locale.ROOT)));
        }

        Map<String, Boolean> overrides = new LinkedHashMap<>();
        for (UserPermission up : userPermissionRepository.findByUserId(user.getId())) {
            overrides.put(up.getPermissionName().toUpperCase(Locale.ROOT), up.getEnabled());
        }

        List<UserPermissionsResponse.Entry> entries = Arrays.stream(AppPermission.values())
                .map(Enum::name)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .map(perm -> {
                    boolean roleDefault = roleEnabled.contains(perm);
                    Boolean override = overrides.get(perm);
                    boolean effective = override != null ? override : roleDefault;
                    return UserPermissionsResponse.Entry.builder()
                            .permission(perm)
                            .roleDefault(roleDefault)
                            .override(override)
                            .effective(effective)
                            .build();
                })
                .toList();

        return UserPermissionsResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .roleName(roleName)
                .entries(entries)
                .build();
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

        // Return explicit disabled rows for missing role/permission pairs so the admin grid is complete.
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
