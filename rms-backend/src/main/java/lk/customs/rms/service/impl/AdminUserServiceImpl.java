package lk.customs.rms.service.impl;

import lk.customs.rms.dto.AdminUserCreateRequest;
import lk.customs.rms.dto.AdminUserResponse;
import lk.customs.rms.dto.AdminUserUpdateRequest;
import lk.customs.rms.dto.DuplicateUserCandidateResponse;
import lk.customs.rms.entity.Role;
import lk.customs.rms.entity.User;
import lk.customs.rms.enums.Status;
import lk.customs.rms.exception.BadRequestException;
import lk.customs.rms.exception.ResourceNotFoundException;
import lk.customs.rms.repository.DocumentRepository;
import lk.customs.rms.repository.RoleRepository;
import lk.customs.rms.repository.UserRepository;
import lk.customs.rms.service.AdminUserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdminUserServiceImpl implements AdminUserService {

    private static final List<String> ALL_ALLOWED_ROLES = List.of("ADMIN", "DC", "DDC", "SDDC", "SC", "ASC", "PMA");

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final DocumentRepository documentRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminUserServiceImpl(UserRepository userRepository,
                                RoleRepository roleRepository,
                                DocumentRepository documentRepository,
                                PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.documentRepository = documentRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Page<AdminUserResponse> list(int page, int size, String search, String role, Boolean active) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "fullName"));
        return userRepository.searchUsers(normalize(search), normalize(role), active, pageable)
                .map(AdminUserResponse::from);
    }

    @Override
    public List<String> allowedRoles() {
        return ALL_ALLOWED_ROLES;
    }

    @Override
    @Transactional
    public AdminUserResponse create(AdminUserCreateRequest request) {
        String username = normalizeRequired(request.getUsername(), "Username is required.");
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new BadRequestException("Username is already in use.");
        }

        Role role = requireRole(request.getRole());

        User user = new User();
        user.setFullName(normalizeRequired(request.getFullName(), "Full name is required."));
        user.setUsername(username);
        user.setEmail(normalizeNullable(request.getEmail()));
        user.setPhone(normalizeNullable(request.getPhone()));
        user.setDepartment(normalizeNullable(request.getDepartment()));
        user.setRole(role);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setIsActive(true);

        return AdminUserResponse.from(userRepository.save(user));
    }

    @Override
    @Transactional
    public AdminUserResponse update(Long userId, AdminUserUpdateRequest request) {
        User user = requireUser(userId);
        user.setFullName(normalizeRequired(request.getFullName(), "Full name is required."));
        user.setEmail(normalizeNullable(request.getEmail()));
        user.setPhone(normalizeNullable(request.getPhone()));
        user.setDepartment(normalizeNullable(request.getDepartment()));
        return AdminUserResponse.from(userRepository.save(user));
    }

    @Override
    @Transactional
    public AdminUserResponse activate(Long userId) {
        User user = requireUser(userId);
        user.setIsActive(true);
        return AdminUserResponse.from(userRepository.save(user));
    }

    @Override
    @Transactional
    public AdminUserResponse deactivate(Long userId, Long fallbackDcUserId) {
        User user = requireUser(userId);
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            return AdminUserResponse.from(user);
        }
        if ("ADMIN".equalsIgnoreCase(user.getRole().getRoleName())) {
            throw new BadRequestException("Admin user cannot be deactivated through workflow transfer endpoint.");
        }

        User fallbackDc = requireUser(fallbackDcUserId);
        if (!Boolean.TRUE.equals(fallbackDc.getIsActive()) || !"DC".equalsIgnoreCase(fallbackDc.getRole().getRoleName())) {
            throw new BadRequestException("Fallback owner must be an active DC user.");
        }
        if (user.getId().equals(fallbackDc.getId())) {
            throw new BadRequestException("Fallback owner cannot be the same as user being deactivated.");
        }

        documentRepository.transferOwnershipForActiveDocuments(user.getId(), fallbackDc.getId(), Status.ISSUED);
        user.setIsActive(false);
        return AdminUserResponse.from(userRepository.save(user));
    }

    @Override
    @Transactional
    public void resetPassword(Long userId, String newPassword) {
        User user = requireUser(userId);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Override
    public String exportCsv(String search, String role, Boolean active) {
        var pageable = PageRequest.of(0, 10_000, Sort.by(Sort.Direction.ASC, "fullName"));
        var users = userRepository.searchUsers(normalize(search), normalize(role), active, pageable).getContent();

        StringBuilder builder = new StringBuilder();
        builder.append("id,fullName,username,email,phone,department,role,active,createdAt\n");
        for (User user : users) {
            builder.append(csv(user.getId()))
                    .append(',').append(csv(user.getFullName()))
                    .append(',').append(csv(user.getUsername()))
                    .append(',').append(csv(user.getEmail()))
                    .append(',').append(csv(user.getPhone()))
                    .append(',').append(csv(user.getDepartment()))
                    .append(',').append(csv(user.getRole() == null ? null : user.getRole().getRoleName()))
                    .append(',').append(csv(user.getIsActive()))
                    .append(',').append(csv(user.getCreatedAt()))
                    .append('\n');
        }
        return builder.toString();
    }

    @Override
    public List<DuplicateUserCandidateResponse> findDuplicateCandidates() {
        List<User> activeUsers = userRepository.findAll().stream()
                .filter(user -> Boolean.TRUE.equals(user.getIsActive()))
                .filter(user -> user.getRole() != null)
                .toList();

        Map<String, List<User>> grouped = activeUsers.stream()
                .collect(Collectors.groupingBy(user ->
                        normalize(user.getFullName()).toLowerCase(Locale.ROOT)
                                + "::"
                                + user.getRole().getRoleName().toUpperCase(Locale.ROOT)));

        return grouped.values().stream()
                .filter(users -> users.size() > 1)
                .map(users -> {
                    User first = users.get(0);
                    return DuplicateUserCandidateResponse.builder()
                            .fullName(first.getFullName())
                            .role(first.getRole().getRoleName())
                            .users(users.stream()
                                    .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                                    .map(AdminUserResponse::from)
                                    .toList())
                            .build();
                })
                .toList();
    }

    @Override
    @Transactional
    public void mergeUsers(Long sourceUserId, Long targetUserId) {
        if (sourceUserId.equals(targetUserId)) {
            throw new BadRequestException("Source and target users must be different.");
        }

        User source = requireUser(sourceUserId);
        User target = requireUser(targetUserId);

        if (!Boolean.TRUE.equals(target.getIsActive())) {
            throw new BadRequestException("Target user must be active.");
        }
        if (source.getRole() == null || target.getRole() == null
                || !source.getRole().getRoleName().equalsIgnoreCase(target.getRole().getRoleName())) {
            throw new BadRequestException("Users must have the same role to merge.");
        }

        documentRepository.transferOwnershipForActiveDocuments(source.getId(), target.getId(), Status.ISSUED);
        source.setIsActive(false);
        userRepository.save(source);
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    private Role requireRole(String roleName) {
        String normalized = normalizeRequired(roleName, "Role is required.").toUpperCase(Locale.ROOT);
        if (!ALL_ALLOWED_ROLES.contains(normalized)) {
            throw new BadRequestException("Invalid role: " + roleName);
        }
        return roleRepository.findByRoleName(normalized)
                .orElseThrow(() -> new BadRequestException("Role not found: " + normalized));
    }

    private String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeNullable(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized;
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new BadRequestException(message);
        }
        return normalized;
    }

    private String csv(Object value) {
        if (value == null) return "";
        String s = String.valueOf(value).replace("\"", "\"\"");
        return "\"" + s + "\"";
    }
}
