package lk.customs.rms.config;

import lk.customs.rms.entity.Role;
import lk.customs.rms.entity.RolePermission;
import lk.customs.rms.enums.AppPermission;
import lk.customs.rms.repository.RolePermissionRepository;
import lk.customs.rms.repository.RoleRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.EnumSet;
import java.util.Set;

@Configuration
@Profile("test")
public class TestSecuritySeedConfig {

    @Bean
    CommandLineRunner seedTestRolesAndPermissions(RoleRepository roleRepository,
                                                  RolePermissionRepository rolePermissionRepository) {
        return args -> {
            Role dc = ensureRole(roleRepository, "DC");
            Role ddc = ensureRole(roleRepository, "DDC");
            Role sddc = ensureRole(roleRepository, "SDDC");
            Role sc = ensureRole(roleRepository, "SC");
            Role asc = ensureRole(roleRepository, "ASC");
            Role pma = ensureRole(roleRepository, "PMA");
            Role admin = ensureRole(roleRepository, "ADMIN");

            seedPermissions(rolePermissionRepository, dc, ddc, sddc, sc, asc, pma, admin);
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
                AppPermission.VIEW_SENT_MESSAGES
        );

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
                AppPermission.VIEW_SENT_MESSAGES
        ));
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
                AppPermission.VIEW_SENT_MESSAGES
        ));
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
        if (rolePermissionRepository.findByRole_IdAndPermissionNameIgnoreCase(role.getId(), permission.name()).isPresent()) {
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
}
