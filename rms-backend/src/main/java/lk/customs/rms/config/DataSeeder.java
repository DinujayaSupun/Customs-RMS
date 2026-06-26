package lk.customs.rms.config;

import lk.customs.rms.entity.Role;
import lk.customs.rms.entity.RolePermission;
import lk.customs.rms.entity.User;
import lk.customs.rms.enums.AppPermission;
import lk.customs.rms.repository.RolePermissionRepository;
import lk.customs.rms.repository.RoleRepository;
import lk.customs.rms.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;

import java.util.EnumSet;
import java.util.Set;

@Configuration
@Profile({ "local", "dev", "e2e" })
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private final String defaultPassword;
    private final String adminPassword;

    public DataSeeder(
            @Value("${app.seed.default-password:}") String defaultPassword,
            @Value("${app.seed.admin-password:}") String adminPassword) {
        this.defaultPassword = defaultPassword;
        this.adminPassword = adminPassword;
    }

    @Bean
    CommandLineRunner seedUsers(RoleRepository roleRepository,
            RolePermissionRepository rolePermissionRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        return args -> {
            Role dc = ensureRole(roleRepository, "DC");
            Role ddc = ensureRole(roleRepository, "DDC");
            Role sddc = ensureRole(roleRepository, "SDDC");
            Role sc = ensureRole(roleRepository, "SC");
            Role asc = ensureRole(roleRepository, "ASC");
            Role pma = ensureRole(roleRepository, "PMA");
            Role admin = ensureRole(roleRepository, "ADMIN");

            // Seed every permission row so the admin permission matrix can toggle values immediately.
            seedPermissions(rolePermissionRepository, dc, ddc, sddc, sc, asc, pma, admin);

            if (!StringUtils.hasText(defaultPassword) || !StringUtils.hasText(adminPassword)) {
                log.warn("Default user seeding is enabled, but seed passwords are missing.");
                return;
            }

            String defaultPasswordHash = passwordEncoder.encode(defaultPassword);

            ensureUser(userRepository, "dc", "Director Customs", defaultPasswordHash, dc);
            ensureUser(userRepository, "ddc", "Deputy Director Customs", defaultPasswordHash, ddc);
            ensureUser(userRepository, "sc", "Senior Superintendent", defaultPasswordHash, sc);
            ensureUser(userRepository, "asc", "Assistant Superintendent", defaultPasswordHash, asc);
            ensureUser(userRepository, "pma", "Personal Management Assistant", defaultPasswordHash, pma);
            ensureUser(userRepository, "admin", "System Administrator", passwordEncoder.encode(adminPassword), admin);
        };
    }

    private void seedPermissions(RolePermissionRepository rolePermissionRepository,
            Role dc,
            Role ddc,
            Role sddc,
            Role sc,
            Role asc,
            Role pma,
            Role admin) {
        Set<AppPermission> allWorkflow = EnumSet.of(
                AppPermission.DELETE_DOCUMENT,
                AppPermission.VIEW_PUBLIC_DOCUMENT,
                AppPermission.VIEW_PRIVATE_DOCUMENT,
                AppPermission.VIEW_OWN_CREATED_DOCUMENTS,
                AppPermission.EDIT_DOCUMENT_DETAILS,
                AppPermission.ADD_REMARK,
                AppPermission.VIEW_REMARKS_WHEN_NOT_REPORT_AT,
                AppPermission.FORWARD_DOCUMENT,
                AppPermission.FORWARD_PUBLIC,
                AppPermission.FORWARD_PRIVATE,
                AppPermission.CHANGE_DOCUMENT_VISIBILITY,
                AppPermission.RETURN_DOCUMENT,
                AppPermission.UPLOAD_ATTACHMENT,
                AppPermission.DELETE_ATTACHMENT,
                AppPermission.MANAGE_DOCUMENT_RECIPIENTS,
                AppPermission.CC_VIEW_DOCUMENT,
                AppPermission.CC_VIEW_ATTACHMENTS,
                AppPermission.CC_UPLOAD_ATTACHMENTS,
                AppPermission.CC_DELETE_OWN_ATTACHMENTS,
                AppPermission.CC_VIEW_TIMELINE,
                AppPermission.CC_VIEW_MINUTES,
                AppPermission.BCC_VIEW_DOCUMENT,
                AppPermission.BCC_VIEW_ATTACHMENTS,
                AppPermission.BCC_UPLOAD_ATTACHMENTS,
                AppPermission.BCC_DELETE_OWN_ATTACHMENTS,
                AppPermission.BCC_VIEW_TIMELINE,
                AppPermission.BCC_VIEW_MINUTES,
                AppPermission.VIEW_SENT_MESSAGES);

        seedRolePermissions(rolePermissionRepository, dc, EnumSet.of(
                AppPermission.CREATE_DOCUMENT,
                AppPermission.DELETE_DOCUMENT,
                AppPermission.VIEW_PUBLIC_DOCUMENT,
                AppPermission.VIEW_PRIVATE_DOCUMENT,
                AppPermission.VIEW_OWN_CREATED_DOCUMENTS,
                AppPermission.VIEW_ALL_DOCUMENTS,
                AppPermission.EDIT_DOCUMENT_DETAILS,
                AppPermission.ADD_REMARK,
                AppPermission.VIEW_REMARKS_WHEN_NOT_REPORT_AT,
                AppPermission.FORWARD_DOCUMENT,
                AppPermission.FORWARD_PUBLIC,
                AppPermission.FORWARD_PRIVATE,
                AppPermission.CHANGE_DOCUMENT_VISIBILITY,
                AppPermission.RETURN_DOCUMENT,
                AppPermission.APPROVE_DOCUMENT,
                AppPermission.REJECT_DOCUMENT,
                AppPermission.ISSUE_DOCUMENT,
                AppPermission.REOPEN_DOCUMENT,
                AppPermission.UPLOAD_ATTACHMENT,
                AppPermission.DELETE_ATTACHMENT,
                AppPermission.VIEW_ALL_HISTORY,
                AppPermission.VIEW_LOGS,
                AppPermission.VIEW_SENT_MESSAGES));
        seedRolePermissions(rolePermissionRepository, ddc, allWorkflow);
        seedRolePermissions(rolePermissionRepository, sddc, allWorkflow);
        seedRolePermissions(rolePermissionRepository, sc, allWorkflow);
        seedRolePermissions(rolePermissionRepository, asc, allWorkflow);
        seedRolePermissions(rolePermissionRepository, pma, EnumSet.of(
                AppPermission.CREATE_DOCUMENT,
                AppPermission.DELETE_DOCUMENT,
                AppPermission.VIEW_PUBLIC_DOCUMENT,
                AppPermission.VIEW_PRIVATE_DOCUMENT,
                AppPermission.VIEW_OWN_CREATED_DOCUMENTS,
                AppPermission.EDIT_DOCUMENT_DETAILS,
                AppPermission.ADD_REMARK,
                AppPermission.VIEW_REMARKS_WHEN_NOT_REPORT_AT,
                AppPermission.FORWARD_DOCUMENT,
                AppPermission.FORWARD_PUBLIC,
                AppPermission.FORWARD_PRIVATE,
                AppPermission.CHANGE_DOCUMENT_VISIBILITY,
                AppPermission.RETURN_DOCUMENT,
                AppPermission.UPLOAD_ATTACHMENT,
                AppPermission.DELETE_ATTACHMENT,
                AppPermission.VIEW_SENT_MESSAGES));
        seedRolePermissions(rolePermissionRepository, admin, EnumSet.allOf(AppPermission.class));
    }

    private void seedRolePermissions(RolePermissionRepository rolePermissionRepository,
            Role role,
            Set<AppPermission> enabledPermissions) {
        for (AppPermission permission : AppPermission.values()) {
            ensureRolePermission(rolePermissionRepository, role, permission, enabledPermissions.contains(permission));
        }
    }

    private void ensureRolePermission(RolePermissionRepository rolePermissionRepository,
            Role role,
            AppPermission permission,
            boolean enabled) {
        if (rolePermissionRepository.findByRole_IdAndPermissionNameIgnoreCase(role.getId(), permission.name())
                .isPresent()) {
            return;
        }

        RolePermission rolePermission = new RolePermission();
        rolePermission.setRole(role);
        rolePermission.setPermissionName(permission.name());
        rolePermission.setEnabled(enabled);
        rolePermissionRepository.save(rolePermission);
    }

    private Role ensureRole(RoleRepository roleRepository, String roleName) {
        return roleRepository.findByRoleName(roleName)
                .orElseGet(() -> roleRepository.save(new Role(roleName)));
    }

    private void ensureUser(UserRepository userRepository,
            String username,
            String fullName,
            String passwordHash,
            Role role) {
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            return;
        }

        User user = new User();
        user.setUsername(username);
        user.setFullName(fullName);
        user.setPasswordHash(passwordHash);
        user.setRole(role);
        user.setIsActive(true);

        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException ex) {
            // Another run may have inserted the same username in parallel; do not fail
            // startup.
            log.warn("Skipping seed user '{}': {}", username, ex.getMostSpecificCause().getMessage());
        }
    }
}
