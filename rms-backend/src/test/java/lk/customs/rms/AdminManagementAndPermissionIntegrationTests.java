package lk.customs.rms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lk.customs.rms.entity.Role;
import lk.customs.rms.entity.RolePermission;
import lk.customs.rms.entity.User;
import lk.customs.rms.enums.AppPermission;
import lk.customs.rms.repository.RolePermissionRepository;
import lk.customs.rms.repository.RoleRepository;
import lk.customs.rms.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.hasItem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class AdminManagementAndPermissionIntegrationTests {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private RolePermissionRepository rolePermissionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void adminCanCreateUserAndDuplicateUsernameIsRejected() throws Exception {
        String password = "Admin1234";
        User admin = createUser("ADMIN", "user-create-admin-", password);
        String adminToken = loginAndGetToken(admin.getUsername(), password);

        String username = "itest-user-" + UUID.randomUUID();

        mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Integration Created User",
                                  "username": "%s",
                                  "email": "created@example.com",
                                  "phone": "0771234567",
                                  "department": "Testing",
                                  "role": "SC",
                                  "password": "Temp1234"
                                }
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.role").value("SC"))
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Duplicate User",
                                  "username": "%s",
                                  "email": "dup@example.com",
                                  "phone": "0771234568",
                                  "department": "Testing",
                                  "role": "SC",
                                  "password": "Temp1234"
                                }
                                """.formatted(username)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Username is already in use."));
    }

    @Test
    void duplicateCandidatesIncludeOnlyActiveNotDeletedUsersWithRoles() throws Exception {
        String password = "Admin1234";
        User admin = createUser("ADMIN", "dup-admin-", password);
        String adminToken = loginAndGetToken(admin.getUsername(), password);

        String duplicateName = "Duplicate Candidate " + UUID.randomUUID();
        User activeOne = createUser("SC", "dup-active-a-", password);
        activeOne.setFullName(duplicateName);
        userRepository.saveAndFlush(activeOne);

        User activeTwo = createUser("SC", "dup-active-b-", password);
        activeTwo.setFullName(duplicateName);
        userRepository.saveAndFlush(activeTwo);

        User differentRole = createUser("PMA", "dup-different-role-", password);
        differentRole.setFullName(duplicateName);
        userRepository.saveAndFlush(differentRole);

        User inactive = createUser("SC", "dup-inactive-", password);
        inactive.setFullName(duplicateName);
        inactive.setIsActive(false);
        userRepository.saveAndFlush(inactive);

        User deleted = createUser("SC", "dup-deleted-", password);
        deleted.setFullName(duplicateName);
        deleted.setIsDeleted(true);
        userRepository.saveAndFlush(deleted);

        mockMvc.perform(get("/api/admin/users/duplicates")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.fullName == '%s' && @.role == 'SC')].users.length()".formatted(duplicateName)).value(hasItem(2)))
                .andExpect(jsonPath("$[?(@.fullName == '%s' && @.role == 'PMA')]".formatted(duplicateName)).isEmpty());
    }

    @Test
    void adminCanDeactivateUserTransferOwnershipAndResetPassword() throws Exception {
        String adminPassword = "Admin1234";
        String userPassword = "Member1234";
        String resetPassword = "Reset5678";

        User admin = createUser("ADMIN", "user-admin-", adminPassword);
        User fallbackDc = createUser("DC", "fallback-dc-", userPassword);
        User workflowUser = createUser("PMA", "deactivate-pma-", userPassword);

        String adminToken = loginAndGetToken(admin.getUsername(), adminPassword);
        String workflowUserToken = loginAndGetToken(workflowUser.getUsername(), userPassword);

        long documentId = createDocument(workflowUser, workflowUserToken, "deactivate-transfer");

        mockMvc.perform(patch("/api/admin/users/{userId}/deactivate", workflowUser.getId())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fallbackDcUserId": %d
                                }
                                """.formatted(fallbackDc.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(workflowUser.getId()))
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "%s"
                                }
                                """.formatted(workflowUser.getUsername(), userPassword)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid username or password."));

        mockMvc.perform(get("/api/documents/{id}", documentId)
                        .header("Authorization", bearer(loginAndGetToken(fallbackDc.getUsername(), userPassword))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentOwnerUserId").value(fallbackDc.getId()));

        mockMvc.perform(patch("/api/admin/users/{userId}/reset-password", workflowUser.getId())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "newPassword": "%s"
                                }
                                """.formatted(resetPassword)))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/admin/users/{userId}/activate", workflowUser.getId())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "%s"
                                }
                                """.formatted(workflowUser.getUsername(), resetPassword)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(workflowUser.getUsername()));
    }

    @Test
    void permissionMatrixUpdateTakesEffectOnNextRequestAndIsRestored() throws Exception {
        String password = "Admin1234";
        User admin = createUser("ADMIN", "perm-admin-", password);
        User scUser = createUser("SC", "perm-sc-", password);

        String adminToken = loginAndGetToken(admin.getUsername(), password);
        String scToken = loginAndGetToken(scUser.getUsername(), password);

        Role scRole = roleRepository.findByRoleName("SC")
                .orElseThrow(() -> new IllegalStateException("Role not found: SC"));
        RolePermission createPermission = rolePermissionRepository
                .findByRole_IdAndPermissionNameIgnoreCase(scRole.getId(), "CREATE_DOCUMENT")
                .orElseThrow(() -> new IllegalStateException("Permission not found for SC CREATE_DOCUMENT"));

        boolean originalEnabled = Boolean.TRUE.equals(createPermission.getEnabled());

        try {
            mockMvc.perform(put("/api/admin/permissions")
                            .header("Authorization", bearer(adminToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "entries": [
                                        {
                                          "roleName": "SC",
                                          "permission": "CREATE_DOCUMENT",
                                          "enabled": false
                                        }
                                      ]
                                    }
                                    """))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/api/documents")
                            .header("Authorization", bearer(scToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(documentPayload("permission-block", "Blocked By Permission", LocalDate.now().toString(), "LOW")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("You are not allowed to create documents."));

            mockMvc.perform(put("/api/admin/permissions")
                            .header("Authorization", bearer(adminToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "entries": [
                                        {
                                          "roleName": "SC",
                                          "permission": "CREATE_DOCUMENT",
                                          "enabled": true
                                        }
                                      ]
                                    }
                                    """))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/api/documents")
                            .header("Authorization", bearer(scToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(documentPayload("permission-allow", "Allowed Again", LocalDate.now().toString(), "LOW")))
                    .andExpect(status().isCreated());
        } finally {
            rolePermissionRepository.findByRole_IdAndPermissionNameIgnoreCase(scRole.getId(), "CREATE_DOCUMENT")
                    .ifPresent(permission -> {
                        permission.setEnabled(originalEnabled);
                        rolePermissionRepository.saveAndFlush(permission);
                    });
        }
    }

    @Test
    void perUserPermissionOverrideRevokesGrantsAndInherits() throws Exception {
        String password = "Admin1234";
        User admin = createUser("ADMIN", "up-admin-", password);
        // PMA role has CREATE_DOCUMENT by default.
        User pma1 = createUser("PMA", "up-pma1-", password);
        User pma2 = createUser("PMA", "up-pma2-", password);

        String adminToken = loginAndGetToken(admin.getUsername(), password);
        String pma1Token = loginAndGetToken(pma1.getUsername(), password);
        String pma2Token = loginAndGetToken(pma2.getUsername(), password);

        // Baseline: inherits the role → pma1 can create.
        mockMvc.perform(post("/api/documents").header("Authorization", bearer(pma1Token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(documentPayload("up-base", "Base", LocalDate.now().toString(), "LOW")))
                .andExpect(status().isCreated());

        // REVOKE CREATE_DOCUMENT for pma1 only.
        mockMvc.perform(put("/api/admin/users/{id}/permissions", pma1.getId())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "entries": [ { "permission": "CREATE_DOCUMENT", "override": false } ] }
                                """))
                .andExpect(status().isOk());

        // pma1 is now blocked...
        mockMvc.perform(post("/api/documents").header("Authorization", bearer(pma1Token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(documentPayload("up-blocked", "Blocked", LocalDate.now().toString(), "LOW")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You are not allowed to create documents."));

        // ...but pma2 (same role, no override) is unaffected → override is per-user, not per-role.
        mockMvc.perform(post("/api/documents").header("Authorization", bearer(pma2Token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(documentPayload("up-other", "Other", LocalDate.now().toString(), "LOW")))
                .andExpect(status().isCreated());

        // GRANT a permission the PMA role lacks, to pma1, and confirm the resolved response.
        mockMvc.perform(put("/api/admin/users/{id}/permissions", pma1.getId())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "entries": [ { "permission": "VIEW_ALL_DOCUMENTS", "override": true } ] }
                                """))
                .andExpect(status().isOk());

        MvcResult permResult = mockMvc.perform(get("/api/admin/users/{id}/permissions", pma1.getId())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode viewAll = null;
        for (JsonNode e : readJson(permResult).get("entries")) {
            if ("VIEW_ALL_DOCUMENTS".equals(e.get("permission").asText())) { viewAll = e; break; }
        }
        assertThat(viewAll).isNotNull();
        assertThat(viewAll.get("roleDefault").asBoolean()).isFalse();
        assertThat(viewAll.get("override").asBoolean()).isTrue();
        assertThat(viewAll.get("effective").asBoolean()).isTrue();

        // INHERIT: null override removes the CREATE_DOCUMENT override → pma1 can create again.
        mockMvc.perform(put("/api/admin/users/{id}/permissions", pma1.getId())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "entries": [ { "permission": "CREATE_DOCUMENT", "override": null } ] }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/documents").header("Authorization", bearer(pma1Token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(documentPayload("up-restored", "Restored", LocalDate.now().toString(), "LOW")))
                .andExpect(status().isCreated());
    }

    @Test
    void nonAdminCannotUpdateUserPermissions() throws Exception {
        String password = "Member1234";
        User pma = createUser("PMA", "up-non-admin-", password);
        String token = loginAndGetToken(pma.getUsername(), password);

        mockMvc.perform(put("/api/admin/users/{id}/permissions", pma.getId())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "entries": [ { "permission": "VIEW_LOGS", "override": true } ] }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void nonAdminCannotUpdatePermissionMatrix() throws Exception {
        String password = "Member1234";
        User scUser = createUser("SC", "perm-non-admin-", password);
        String scToken = loginAndGetToken(scUser.getUsername(), password);

        mockMvc.perform(put("/api/admin/permissions")
                        .header("Authorization", bearer(scToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "entries": [
                                    {
                                      "roleName": "SC",
                                      "permission": "CREATE_DOCUMENT",
                                      "enabled": false
                                    }
                                  ]
                                }
                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanSavePermissionPageMatrixAndWorkflowConfigTogether() throws Exception {
        String password = "Admin1234";
        User admin = createUser("ADMIN", "perm-page-admin-", password);
        User receiver = createUser("DDC", "perm-page-receiver-", password);
        String adminToken = loginAndGetToken(admin.getUsername(), password);

        Role scRole = requireRole("SC");
        RolePermission createPermission = rolePermissionRepository
                .findByRole_IdAndPermissionNameIgnoreCase(scRole.getId(), AppPermission.CREATE_DOCUMENT.name())
                .orElseThrow(() -> new IllegalStateException("Permission not found for SC CREATE_DOCUMENT"));
        boolean originalEnabled = Boolean.TRUE.equals(createPermission.getEnabled());

        try {
            mockMvc.perform(put("/api/admin/permissions/page")
                            .header("Authorization", bearer(adminToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "permissionMatrix": {
                                        "entries": [
                                          {
                                            "roleName": "SC",
                                            "permission": "CREATE_DOCUMENT",
                                            "enabled": false
                                          }
                                        ]
                                      },
                                      "dcAutoForwardConfig": {
                                        "enabled": true,
                                        "timeoutMinutes": 15,
                                        "receiverUserId": %d,
                                        "forwardReturnAllowedStatuses": ["PENDING", "RETURNED"],
                                        "approveRejectButtonsEnabled": false,
                                        "undoSendEnabled": true,
                                        "undoSendWindowHours": 12,
                                        "undoSendRequiresUnopened": true,
                                        "undoSendAllowedActions": ["FORWARD"],
                                        "undoSendRequiresReason": true,
                                        "undoSendNotifyReceiver": false,
                                        "undoSendShowExpiredInfo": true
                                      }
                                    }
                                    """.formatted(receiver.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.permissionMatrix.entries[?(@.roleName == 'SC' && @.permission == 'CREATE_DOCUMENT')].enabled").value(hasItem(false)))
                    .andExpect(jsonPath("$.dcAutoForwardConfig.enabled").value(true))
                    .andExpect(jsonPath("$.dcAutoForwardConfig.timeoutMinutes").value(15))
                    .andExpect(jsonPath("$.dcAutoForwardConfig.receiverUserId").value(receiver.getId()))
                    .andExpect(jsonPath("$.dcAutoForwardConfig.forwardReturnAllowedStatuses").value(hasItem("PENDING")))
                    .andExpect(jsonPath("$.dcAutoForwardConfig.forwardReturnAllowedStatuses").value(hasItem("RETURNED")))
                    .andExpect(jsonPath("$.dcAutoForwardConfig.approveRejectButtonsEnabled").value(false))
                    .andExpect(jsonPath("$.dcAutoForwardConfig.undoSendAllowedActions").value(hasItem("FORWARD")));
        } finally {
            restorePermission(scRole, AppPermission.CREATE_DOCUMENT, originalEnabled);
        }
    }

    @Test
    void adminConfiguresPerDcAutoForwardReceiverMapping() throws Exception {
        String password = "Admin1234";
        User admin = createUser("ADMIN", "dc-map-admin-", password);
        User dcA = createUser("DC", "dc-map-dcA-", password);
        User dcB = createUser("DC", "dc-map-dcB-", password);
        User receiverA = createUser("DDC", "dc-map-receiverA-", password);
        User receiverB = createUser("SDDC", "dc-map-receiverB-", password);
        String adminToken = loginAndGetToken(admin.getUsername(), password);

        try {
            mockMvc.perform(put("/api/admin/permissions/dc-auto-forward")
                            .header("Authorization", bearer(adminToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "enabled": true,
                                      "timeoutMinutes": 30,
                                      "approveRejectButtonsEnabled": true,
                                      "dcReceivers": [
                                        { "dcUserId": %d, "receiverUserId": %d },
                                        { "dcUserId": %d, "receiverUserId": %d }
                                      ]
                                    }
                                    """.formatted(dcA.getId(), receiverA.getId(), dcB.getId(), receiverB.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.dcReceivers.length()").value(2))
                    .andExpect(jsonPath("$.dcReceivers[?(@.dcUserId == " + dcA.getId() + ")].receiverUserId").value(hasItem(receiverA.getId().intValue())))
                    .andExpect(jsonPath("$.dcReceivers[?(@.dcUserId == " + dcB.getId() + ")].receiverUserId").value(hasItem(receiverB.getId().intValue())))
                    .andExpect(jsonPath("$.dcReceivers[?(@.dcUserId == " + dcA.getId() + ")].receiverName").value(hasItem(receiverA.getFullName())));

            // GET reflects the same mapping.
            mockMvc.perform(get("/api/admin/permissions/dc-auto-forward")
                            .header("Authorization", bearer(adminToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.dcReceivers.length()").value(2));

            // Re-saving with only dcA present removes the dcB mapping (full replace semantics).
            mockMvc.perform(put("/api/admin/permissions/dc-auto-forward")
                            .header("Authorization", bearer(adminToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "enabled": true,
                                      "timeoutMinutes": 30,
                                      "approveRejectButtonsEnabled": true,
                                      "dcReceivers": [
                                        { "dcUserId": %d, "receiverUserId": %d }
                                      ]
                                    }
                                    """.formatted(dcA.getId(), receiverA.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.dcReceivers.length()").value(1));

            // A non-DC dcUserId is rejected.
            mockMvc.perform(put("/api/admin/permissions/dc-auto-forward")
                            .header("Authorization", bearer(adminToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "enabled": true,
                                      "timeoutMinutes": 30,
                                      "approveRejectButtonsEnabled": true,
                                      "dcReceivers": [ { "dcUserId": %d, "receiverUserId": %d } ]
                                    }
                                    """.formatted(admin.getId(), receiverA.getId())))
                    .andExpect(status().isBadRequest());

            // A receiver without DDC/SDDC role is rejected.
            mockMvc.perform(put("/api/admin/permissions/dc-auto-forward")
                            .header("Authorization", bearer(adminToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "enabled": true,
                                      "timeoutMinutes": 30,
                                      "approveRejectButtonsEnabled": true,
                                      "dcReceivers": [ { "dcUserId": %d, "receiverUserId": %d } ]
                                    }
                                    """.formatted(dcA.getId(), dcB.getId())))
                    .andExpect(status().isBadRequest());
        } finally {
            // Clean up through the real API (enabled=false allows an empty dcReceivers list),
            // rather than a raw repository delete call outside of a managed transaction.
            mockMvc.perform(put("/api/admin/permissions/dc-auto-forward")
                    .header("Authorization", bearer(adminToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "enabled": false,
                              "timeoutMinutes": 30,
                              "approveRejectButtonsEnabled": true,
                              "dcReceivers": []
                            }
                            """));
        }
    }

    @Test
    void defaultRolePermissionMatrixMatchesSeededConfiguration() {
        for (String roleName : allRoleNames().toList()) {
            Role role = roleRepository.findByRoleName(roleName)
                    .orElseThrow(() -> new IllegalStateException("Role not found: " + roleName));

            List<RolePermission> permissions = rolePermissionRepository.findByRole_RoleNameIgnoreCaseOrderByPermissionNameAsc(roleName);
            assertThat(permissions)
                    .hasSize(AppPermission.values().length);

            Set<String> enabledPermissions = permissions.stream()
                    .filter(permission -> Boolean.TRUE.equals(permission.getEnabled()))
                    .map(RolePermission::getPermissionName)
                    .collect(java.util.stream.Collectors.toSet());

            Set<String> expectedEnabledPermissions = expectedEnabledPermissionsForRole(roleName).stream()
                    .map(Enum::name)
                    .collect(java.util.stream.Collectors.toSet());

            assertThat(enabledPermissions)
                    .as("enabled permissions for role %s", roleName)
                    .containsExactlyInAnyOrderElementsOf(expectedEnabledPermissions);
        }
    }

    @ParameterizedTest(name = "{0} create-document permission can be revoked and granted live")
    @MethodSource("allRoleNames")
    void createDocumentPermissionUpdateTakesEffectForEveryRole(String roleName) throws Exception {
        String password = "PermCreate123";
        User admin = createUser("ADMIN", "perm-admin-", password);
        User actor = createUser(roleName, "perm-create-" + roleName.toLowerCase() + "-", password);

        String adminToken = loginAndGetToken(admin.getUsername(), password);
        String actorToken = loginAndGetToken(actor.getUsername(), password);

        Role role = roleRepository.findByRoleName(roleName)
                .orElseThrow(() -> new IllegalStateException("Role not found: " + roleName));
        RolePermission permission = rolePermissionRepository
                .findByRole_IdAndPermissionNameIgnoreCase(role.getId(), AppPermission.CREATE_DOCUMENT.name())
                .orElseThrow(() -> new IllegalStateException("Permission not found for " + roleName + " CREATE_DOCUMENT"));

        boolean originalEnabled = Boolean.TRUE.equals(permission.getEnabled());

        try {
            updatePermission(adminToken, roleName, AppPermission.CREATE_DOCUMENT, false);

            mockMvc.perform(post("/api/documents")
                            .header("Authorization", bearer(actorToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(documentPayload("permission-block", roleName + " blocked", LocalDate.now().toString(), "LOW")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("You are not allowed to create documents."));

            updatePermission(adminToken, roleName, AppPermission.CREATE_DOCUMENT, true);

            mockMvc.perform(post("/api/documents")
                            .header("Authorization", bearer(actorToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(documentPayload("permission-allow", roleName + " allowed", LocalDate.now().toString(), "LOW")))
                    .andExpect(status().isCreated());
        } finally {
            restorePermission(role, AppPermission.CREATE_DOCUMENT, originalEnabled);
        }
    }

    @ParameterizedTest(name = "{0} view-logs permission can be revoked and granted live")
    @MethodSource("allRoleNames")
    void viewLogsPermissionUpdateTakesEffectForEveryRole(String roleName) throws Exception {
        String password = "PermLogs123";
        User admin = createUser("ADMIN", "logs-admin-", password);
        User actor = createUser(roleName, "logs-role-" + roleName.toLowerCase() + "-", password);

        String adminToken = loginAndGetToken(admin.getUsername(), password);
        String actorToken = loginAndGetToken(actor.getUsername(), password);

        Role role = roleRepository.findByRoleName(roleName)
                .orElseThrow(() -> new IllegalStateException("Role not found: " + roleName));
        RolePermission permission = rolePermissionRepository
                .findByRole_IdAndPermissionNameIgnoreCase(role.getId(), AppPermission.VIEW_LOGS.name())
                .orElseThrow(() -> new IllegalStateException("Permission not found for " + roleName + " VIEW_LOGS"));

        boolean originalEnabled = Boolean.TRUE.equals(permission.getEnabled());

        try {
            updatePermission(adminToken, roleName, AppPermission.VIEW_LOGS, false);

            mockMvc.perform(get("/api/audit-logs")
                            .header("Authorization", bearer(actorToken)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("You are not allowed to view logs."));

            updatePermission(adminToken, roleName, AppPermission.VIEW_LOGS, true);

            mockMvc.perform(get("/api/audit-logs")
                            .header("Authorization", bearer(actorToken)))
                    .andExpect(status().isOk());
        } finally {
            restorePermission(role, AppPermission.VIEW_LOGS, originalEnabled);
        }
    }

    @ParameterizedTest(name = "{0} forward permission can be revoked and granted live")
    @MethodSource("allRoleNames")
    void forwardPermissionUpdateTakesEffectForEveryRole(String roleName) throws Exception {
        String password = "PermForward123";
        User admin = createUser("ADMIN", "forward-admin-", password);
        User actor = createUser(roleName, "forward-actor-" + roleName.toLowerCase() + "-", password);
        User recipient = createUser("SC", "forward-recipient-", password);

        String adminToken = loginAndGetToken(admin.getUsername(), password);
        String actorToken = loginAndGetToken(actor.getUsername(), password);

        Role role = requireRole(roleName);
        boolean originalForward = readPermissionEnabled(role, AppPermission.FORWARD_DOCUMENT);

        try {
            long blockedDocumentId = createWorkflowOwnedDocument(admin, adminToken, actor, actorToken, "forward-blocked");
            updatePermission(adminToken, roleName, AppPermission.FORWARD_DOCUMENT, false);

            mockMvc.perform(post("/api/documents/{id}/forward", blockedDocumentId)
                            .header("Authorization", bearer(actorToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "toUserId": %d,
                                      "forwardVisibility": "PUBLIC",
                                      "remarkText": "Blocked forward"
                                    }
                                    """.formatted(recipient.getId())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("You are not allowed to forward documents."));

            long allowedDocumentId = createWorkflowOwnedDocument(admin, adminToken, actor, actorToken, "forward-allowed");
            updatePermission(adminToken, roleName, AppPermission.FORWARD_DOCUMENT, true);

            mockMvc.perform(post("/api/documents/{id}/forward", allowedDocumentId)
                            .header("Authorization", bearer(actorToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "toUserId": %d,
                                      "forwardVisibility": "PUBLIC",
                                      "remarkText": "Allowed forward"
                                    }
                                    """.formatted(recipient.getId())))
                    .andExpect(status().isOk());
        } finally {
            restorePermission(role, AppPermission.FORWARD_DOCUMENT, originalForward);
        }
    }

    @ParameterizedTest(name = "{0} return permission can be revoked and granted live")
    @MethodSource("allRoleNames")
    void returnPermissionUpdateTakesEffectForEveryRole(String roleName) throws Exception {
        String password = "PermReturn123";
        User admin = createUser("ADMIN", "return-admin-", password);
        User actor = createUser(roleName, "return-actor-" + roleName.toLowerCase() + "-", password);
        User returnTarget = "ADMIN".equals(roleName)
                ? createUser("DC", "return-target-", password)
                : admin;

        String adminToken = loginAndGetToken(admin.getUsername(), password);
        String actorToken = loginAndGetToken(actor.getUsername(), password);

        Role role = requireRole(roleName);
        boolean originalReturn = readPermissionEnabled(role, AppPermission.RETURN_DOCUMENT);

        try {
            // Admin forwards the document to the actor so there is a real prior sender to return to;
            // return derives its target from the movement history, not the request body.
            long blockedDocumentId = createOwnedDocumentForActor(admin, adminToken, actor.getId(), "return-blocked");
            updatePermission(adminToken, roleName, AppPermission.RETURN_DOCUMENT, false);

            mockMvc.perform(post("/api/documents/{id}/return", blockedDocumentId)
                            .header("Authorization", bearer(actorToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "toUserId": %d,
                                      "remarkText": "Blocked return"
                                    }
                                    """.formatted(returnTarget.getId())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("You are not allowed to return documents."));

            long allowedDocumentId = createOwnedDocumentForActor(admin, adminToken, actor.getId(), "return-allowed");
            updatePermission(adminToken, roleName, AppPermission.RETURN_DOCUMENT, true);

            mockMvc.perform(post("/api/documents/{id}/return", allowedDocumentId)
                            .header("Authorization", bearer(actorToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "toUserId": %d,
                                      "remarkText": "Allowed return"
                                    }
                                    """.formatted(returnTarget.getId())))
                    .andExpect(status().isOk());
        } finally {
            restorePermission(role, AppPermission.RETURN_DOCUMENT, originalReturn);
        }
    }

    @ParameterizedTest(name = "{0} approve permission can be revoked and granted live")
    @MethodSource("allRoleNames")
    void approvePermissionUpdateTakesEffectForEveryRole(String roleName) throws Exception {
        String password = "PermApprove123";
        User admin = createUser("ADMIN", "approve-admin-", password);
        User actor = createUser(roleName, "approve-actor-" + roleName.toLowerCase() + "-", password);

        String adminToken = loginAndGetToken(admin.getUsername(), password);
        String actorToken = loginAndGetToken(actor.getUsername(), password);

        Role role = requireRole(roleName);
        boolean originalApprove = readPermissionEnabled(role, AppPermission.APPROVE_DOCUMENT);

        try {
            long blockedDocumentId = createWorkflowOwnedDocument(admin, adminToken, actor, actorToken, "approve-blocked");
            updatePermission(adminToken, roleName, AppPermission.APPROVE_DOCUMENT, false);

            mockMvc.perform(post("/api/documents/{id}/approve", blockedDocumentId)
                            .header("Authorization", bearer(actorToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"remarkText\":\"Blocked approve\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("You are not allowed to approve documents."));

            long allowedDocumentId = createWorkflowOwnedDocument(admin, adminToken, actor, actorToken, "approve-allowed");
            updatePermission(adminToken, roleName, AppPermission.APPROVE_DOCUMENT, true);

            mockMvc.perform(post("/api/documents/{id}/approve", allowedDocumentId)
                            .header("Authorization", bearer(actorToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"remarkText\":\"Allowed approve\"}"))
                    .andExpect(status().isOk());
        } finally {
            restorePermission(role, AppPermission.APPROVE_DOCUMENT, originalApprove);
        }
    }

    @ParameterizedTest(name = "{0} reject permission can be revoked and granted live")
    @MethodSource("allRoleNames")
    void rejectPermissionUpdateTakesEffectForEveryRole(String roleName) throws Exception {
        String password = "PermReject123";
        User admin = createUser("ADMIN", "reject-admin-", password);
        User actor = createUser(roleName, "reject-actor-" + roleName.toLowerCase() + "-", password);

        String adminToken = loginAndGetToken(admin.getUsername(), password);
        String actorToken = loginAndGetToken(actor.getUsername(), password);

        Role role = requireRole(roleName);
        boolean originalReject = readPermissionEnabled(role, AppPermission.REJECT_DOCUMENT);

        try {
            long blockedDocumentId = createWorkflowOwnedDocument(admin, adminToken, actor, actorToken, "reject-blocked");
            updatePermission(adminToken, roleName, AppPermission.REJECT_DOCUMENT, false);

            mockMvc.perform(post("/api/documents/{id}/reject", blockedDocumentId)
                            .header("Authorization", bearer(actorToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"remarkText\":\"Blocked reject\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("You are not allowed to reject documents."));

            long allowedDocumentId = createWorkflowOwnedDocument(admin, adminToken, actor, actorToken, "reject-allowed");
            updatePermission(adminToken, roleName, AppPermission.REJECT_DOCUMENT, true);

            mockMvc.perform(post("/api/documents/{id}/reject", allowedDocumentId)
                            .header("Authorization", bearer(actorToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"remarkText\":\"Allowed reject\"}"))
                    .andExpect(status().isOk());
        } finally {
            restorePermission(role, AppPermission.REJECT_DOCUMENT, originalReject);
        }
    }

    @ParameterizedTest(name = "{0} issue permission can be revoked and granted live")
    @MethodSource("allRoleNames")
    void issuePermissionUpdateTakesEffectForEveryRole(String roleName) throws Exception {
        String password = "PermIssue123";
        User admin = createUser("ADMIN", "issue-admin-", password);
        User actor = createUser(roleName, "issue-actor-" + roleName.toLowerCase() + "-", password);

        String adminToken = loginAndGetToken(admin.getUsername(), password);
        String actorToken = loginAndGetToken(actor.getUsername(), password);

        Role role = requireRole(roleName);
        boolean originalApprove = readPermissionEnabled(role, AppPermission.APPROVE_DOCUMENT);
        boolean originalIssue = readPermissionEnabled(role, AppPermission.ISSUE_DOCUMENT);

        try {
            updatePermission(adminToken, roleName, AppPermission.APPROVE_DOCUMENT, true);

            long blockedDocumentId = createWorkflowOwnedDocument(admin, adminToken, actor, actorToken, "issue-blocked");
            approveDocumentAsActor(blockedDocumentId, actorToken);
            updatePermission(adminToken, roleName, AppPermission.ISSUE_DOCUMENT, false);

            mockMvc.perform(post("/api/documents/{id}/issue", blockedDocumentId)
                            .header("Authorization", bearer(actorToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"remarkText\":\"Blocked issue\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("You are not allowed to complete documents."));

            long allowedDocumentId = createWorkflowOwnedDocument(admin, adminToken, actor, actorToken, "issue-allowed");
            approveDocumentAsActor(allowedDocumentId, actorToken);
            updatePermission(adminToken, roleName, AppPermission.ISSUE_DOCUMENT, true);

            mockMvc.perform(post("/api/documents/{id}/issue", allowedDocumentId)
                            .header("Authorization", bearer(actorToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"remarkText\":\"Allowed issue\"}"))
                    .andExpect(status().isOk());
        } finally {
            restorePermission(role, AppPermission.APPROVE_DOCUMENT, originalApprove);
            restorePermission(role, AppPermission.ISSUE_DOCUMENT, originalIssue);
        }
    }

    @ParameterizedTest(name = "{0} reopen permission can be revoked and granted live")
    @MethodSource("allRoleNames")
    void reopenPermissionUpdateTakesEffectForEveryRole(String roleName) throws Exception {
        String password = "PermReopen123";
        User admin = createUser("ADMIN", "reopen-admin-", password);
        User actor = createUser(roleName, "reopen-actor-" + roleName.toLowerCase() + "-", password);

        String adminToken = loginAndGetToken(admin.getUsername(), password);
        String actorToken = loginAndGetToken(actor.getUsername(), password);

        Role role = requireRole(roleName);
        boolean originalReject = readPermissionEnabled(role, AppPermission.REJECT_DOCUMENT);
        boolean originalReopen = readPermissionEnabled(role, AppPermission.REOPEN_DOCUMENT);

        try {
            updatePermission(adminToken, roleName, AppPermission.REJECT_DOCUMENT, true);

            long blockedDocumentId = createWorkflowOwnedDocument(admin, adminToken, actor, actorToken, "reopen-blocked");
            rejectDocumentAsActor(blockedDocumentId, actorToken);
            updatePermission(adminToken, roleName, AppPermission.REOPEN_DOCUMENT, false);

            mockMvc.perform(post("/api/documents/{id}/reopen", blockedDocumentId)
                            .header("Authorization", bearer(actorToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"remarkText\":\"Blocked reopen\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("You are not allowed to reopen documents."));

            long allowedDocumentId = createWorkflowOwnedDocument(admin, adminToken, actor, actorToken, "reopen-allowed");
            rejectDocumentAsActor(allowedDocumentId, actorToken);
            updatePermission(adminToken, roleName, AppPermission.REOPEN_DOCUMENT, true);

            mockMvc.perform(post("/api/documents/{id}/reopen", allowedDocumentId)
                            .header("Authorization", bearer(actorToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"remarkText\":\"Allowed reopen\"}"))
                    .andExpect(status().isOk());
        } finally {
            restorePermission(role, AppPermission.REJECT_DOCUMENT, originalReject);
            restorePermission(role, AppPermission.REOPEN_DOCUMENT, originalReopen);
        }
    }

    @Test
    void editDetailsRequiresCurrentOwnerAndEditPermission() throws Exception {
        String password = "EditDetails123";
        User admin = createUser("ADMIN", "edit-admin-", password);
        User actor = createUser("SC", "edit-actor-", password);

        String adminToken = loginAndGetToken(admin.getUsername(), password);
        String actorToken = loginAndGetToken(actor.getUsername(), password);

        Role actorRole = requireRole("SC");
        boolean originalEdit = readPermissionEnabled(actorRole, AppPermission.EDIT_DOCUMENT_DETAILS);

        try {
            long documentId = createOwnedDocumentForActor(admin, adminToken, actor.getId(), "edit-details");

            mockMvc.perform(put("/api/documents/{id}", documentId)
                            .header("Authorization", bearer(adminToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(documentPayload("non-owner-edit", "Blocked Non Owner Edit", LocalDate.now().toString(), "HIGH")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Only the current owner can edit document details."));

            updatePermission(adminToken, "SC", AppPermission.EDIT_DOCUMENT_DETAILS, false);

            mockMvc.perform(put("/api/documents/{id}", documentId)
                            .header("Authorization", bearer(actorToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(documentPayload("permission-blocked-edit", "Blocked Permission Edit", LocalDate.now().toString(), "HIGH")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("You are not allowed to edit document details."));

            updatePermission(adminToken, "SC", AppPermission.EDIT_DOCUMENT_DETAILS, true);

            mockMvc.perform(put("/api/documents/{id}", documentId)
                            .header("Authorization", bearer(actorToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(documentPayload("allowed-edit", "Allowed Owner Edit", LocalDate.now().toString(), "LOW")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("Allowed Owner Edit"))
                    .andExpect(jsonPath("$.priority").value("LOW"));
        } finally {
            restorePermission(actorRole, AppPermission.EDIT_DOCUMENT_DETAILS, originalEdit);
        }
    }

    @Test
    void addMinuteRequiresCurrentOwnerAndAddRemarkPermission() throws Exception {
        String password = "Minute1234";
        User admin = createUser("ADMIN", "minute-admin-", password);
        User actor = createUser("SC", "minute-actor-", password);

        String adminToken = loginAndGetToken(admin.getUsername(), password);
        String actorToken = loginAndGetToken(actor.getUsername(), password);

        Role actorRole = requireRole("SC");
        boolean originalAddRemark = readPermissionEnabled(actorRole, AppPermission.ADD_REMARK);

        try {
            long documentId = createOwnedDocumentForActor(admin, adminToken, actor.getId(), "minute-permission");

            mockMvc.perform(post("/api/documents/{documentId}/remarks", documentId)
                            .header("Authorization", bearer(adminToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"remarkText\":\"Non-owner minute should fail\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Only the current owner can add remarks."));

            updatePermission(adminToken, "SC", AppPermission.ADD_REMARK, false);

            mockMvc.perform(post("/api/documents/{documentId}/remarks", documentId)
                            .header("Authorization", bearer(actorToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"remarkText\":\"Permission blocked minute\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("You are not allowed to add minutes."));

            updatePermission(adminToken, "SC", AppPermission.ADD_REMARK, true);

            mockMvc.perform(post("/api/documents/{documentId}/remarks", documentId)
                            .header("Authorization", bearer(actorToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"remarkText\":\"Allowed owner minute\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.remarkText").value("Allowed owner minute"))
                    .andExpect(jsonPath("$.remarkedByUserId").value(actor.getId()));
        } finally {
            restorePermission(actorRole, AppPermission.ADD_REMARK, originalAddRemark);
        }
    }

    @Test
    void nonOwnerMinuteViewRequiresDedicatedPermission() throws Exception {
        String password = "ViewMinute123";
        User admin = createUser("ADMIN", "view-minute-admin-", password);
        User owner = createUser("DC", "view-minute-owner-", password);
        User outsider = createUser("SC", "view-minute-outsider-", password);

        String adminToken = loginAndGetToken(admin.getUsername(), password);
        String ownerToken = loginAndGetToken(owner.getUsername(), password);
        String outsiderToken = loginAndGetToken(outsider.getUsername(), password);

        Role outsiderRole = requireRole("SC");
        boolean originalViewRemarks = readPermissionEnabled(outsiderRole, AppPermission.VIEW_REMARKS_WHEN_NOT_REPORT_AT);

        try {
            long documentId = createOwnedDocumentForActor(admin, adminToken, owner.getId(), "minute-view");

            mockMvc.perform(post("/api/documents/{documentId}/remarks", documentId)
                            .header("Authorization", bearer(ownerToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"remarkText\":\"Minute visible by permission\"}"))
                    .andExpect(status().isCreated());

            updatePermission(adminToken, "SC", AppPermission.VIEW_REMARKS_WHEN_NOT_REPORT_AT, false);

            mockMvc.perform(get("/api/documents/{documentId}/remarks", documentId)
                            .header("Authorization", bearer(outsiderToken)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("You are not allowed to view minutes unless the document is assigned to you in Report At."));

            updatePermission(adminToken, "SC", AppPermission.VIEW_REMARKS_WHEN_NOT_REPORT_AT, true);

            mockMvc.perform(get("/api/documents/{documentId}/remarks", documentId)
                            .header("Authorization", bearer(outsiderToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].remarkText").value(hasItem("Minute visible by permission")));
        } finally {
            restorePermission(outsiderRole, AppPermission.VIEW_REMARKS_WHEN_NOT_REPORT_AT, originalViewRemarks);
        }
    }

    @Test
    void movementHistoryRequiresCurrentOwnerOrViewAllHistoryPermission() throws Exception {
        String password = "History1234";
        User admin = createUser("ADMIN", "history-admin-", password);
        User owner = createUser("SC", "history-owner-", password);
        User outsider = createUser("PMA", "history-outsider-", password);
        User historyViewer = createUser("DC", "history-viewer-", password);

        String adminToken = loginAndGetToken(admin.getUsername(), password);
        String ownerToken = loginAndGetToken(owner.getUsername(), password);
        String outsiderToken = loginAndGetToken(outsider.getUsername(), password);
        String historyViewerToken = loginAndGetToken(historyViewer.getUsername(), password);

        long documentId = createOwnedDocumentForActor(admin, adminToken, owner.getId(), "history-access");

        mockMvc.perform(get("/api/documents/{documentId}/movements", documentId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].actionType").value("CREATE"));

        mockMvc.perform(get("/api/documents/{documentId}/movements", documentId)
                        .header("Authorization", bearer(outsiderToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You are not allowed to view movement history for this document."));

        mockMvc.perform(get("/api/documents/{documentId}/movements", documentId)
                        .header("Authorization", bearer(historyViewerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].actionType").value("CREATE"));
    }

    @Test
    void adminCanMergeUsersWithSameRoleTransferringActiveDocumentsToTarget() throws Exception {
        String password = "Merge1234";
        User admin = createUser("ADMIN", "merge-admin-", password);
        User source = createUser("DC", "merge-source-", password);
        User target = createUser("DC", "merge-target-", password);

        String adminToken = loginAndGetToken(admin.getUsername(), password);
        String sourceToken = loginAndGetToken(source.getUsername(), password);
        String targetToken = loginAndGetToken(target.getUsername(), password);

        long documentId = createDocument(source, sourceToken, "merge-doc");

        mockMvc.perform(post("/api/admin/users/merge")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceUserId": %d,
                                  "targetUserId": %d
                                }
                                """.formatted(source.getId(), target.getId())))
                .andExpect(status().isOk());

        // Document transferred to target
        mockMvc.perform(get("/api/documents/{id}", documentId)
                        .header("Authorization", bearer(targetToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentOwnerUserId").value(target.getId()));

        // Source is deactivated
        assertThat(userRepository.findById(source.getId()).orElseThrow().getIsActive()).isFalse();

        // Same user → 400
        mockMvc.perform(post("/api/admin/users/merge")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceUserId": %d,
                                  "targetUserId": %d
                                }
                                """.formatted(target.getId(), target.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Source and target users must be different."));

        // Different roles → 400
        User sc = createUser("SC", "merge-sc-", password);
        mockMvc.perform(post("/api/admin/users/merge")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceUserId": %d,
                                  "targetUserId": %d
                                }
                                """.formatted(sc.getId(), target.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Users must have the same role to merge."));
    }

    @Test
    void auditLogCsvExportReturnsCsvFileWithHeaderAndDataRows() throws Exception {
        String password = "CsvExport123";
        User admin = createUser("ADMIN", "csv-export-admin-", password);
        String adminToken = loginAndGetToken(admin.getUsername(), password);

        // Create a document via API — DocumentServiceImpl logs a DOCUMENT/CREATE entry
        createDocument(admin, adminToken, "csv-export-doc");

        MvcResult result = mockMvc.perform(get("/api/audit-logs/export")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn();

        String contentDisposition = result.getResponse().getHeader("Content-Disposition");
        assertThat(contentDisposition).startsWith("attachment; filename=\"audit-logs-");
        assertThat(contentDisposition).endsWith(".csv\"");

        String body = result.getResponse().getContentAsString();
        assertThat(body).startsWith("id,performedAt,actionType,entityType,entityId,performedByUserId,performedByUserName,message,detailsJson");
        assertThat(body.lines().count()).isGreaterThan(1);
    }

    @Test
    void adminCanBulkDeactivateUsersAndTransferActiveDocumentsToFallbackDc() throws Exception {
        String password = "BulkDeact123";
        User admin = createUser("ADMIN", "bulk-deact-admin-", password);
        User fallbackDc = createUser("DC", "bulk-deact-fallback-", password);
        User userA = createUser("DC", "bulk-deact-a-", password);
        User userB = createUser("DC", "bulk-deact-b-", password);

        String adminToken = loginAndGetToken(admin.getUsername(), password);
        String userAToken = loginAndGetToken(userA.getUsername(), password);
        String fallbackToken = loginAndGetToken(fallbackDc.getUsername(), password);

        long documentId = createDocument(userA, userAToken, "bulk-deact-doc");

        mockMvc.perform(post("/api/admin/users/bulk-deactivate")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userIds": [%d, %d],
                                  "fallbackDcUserId": %d
                                }
                                """.formatted(userA.getId(), userB.getId(), fallbackDc.getId())))
                .andExpect(status().isOk());

        assertThat(userRepository.findById(userA.getId()).orElseThrow().getIsActive()).isFalse();
        assertThat(userRepository.findById(userB.getId()).orElseThrow().getIsActive()).isFalse();

        // The deactivated owner's active document must move to the fallback DC so Report At stays valid.
        mockMvc.perform(get("/api/documents/{id}", documentId)
                        .header("Authorization", bearer(fallbackToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentOwnerUserId").value(fallbackDc.getId()));
    }

    @Test
    void bulkDeactivateRejectsEmptyUserIdList() throws Exception {
        String password = "BulkDeact123";
        User admin = createUser("ADMIN", "bulk-deact-empty-admin-", password);
        User fallbackDc = createUser("DC", "bulk-deact-empty-fallback-", password);
        String adminToken = loginAndGetToken(admin.getUsername(), password);

        mockMvc.perform(post("/api/admin/users/bulk-deactivate")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userIds": [],
                                  "fallbackDcUserId": %d
                                }
                                """.formatted(fallbackDc.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", hasItem("userIds: Select at least one user to deactivate.")));
    }

    @Test
    void bulkDeleteRequiresDeactivationFirstThenSoftDeletesAndScrubsPii() throws Exception {
        String password = "BulkDel1234";
        User admin = createUser("ADMIN", "bulk-del-admin-", password);
        User fallbackDc = createUser("DC", "bulk-del-fallback-", password);
        User userA = createUser("DC", "bulk-del-a-", password);
        userA.setEmail("bulk-del-a@example.com");
        userRepository.saveAndFlush(userA);

        String adminToken = loginAndGetToken(admin.getUsername(), password);

        // Active users cannot be deleted; they must be deactivated first.
        mockMvc.perform(post("/api/admin/users/bulk-delete")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userIds": [%d]
                                }
                                """.formatted(userA.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only deactivated users can be deleted."));

        mockMvc.perform(post("/api/admin/users/bulk-deactivate")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userIds": [%d],
                                  "fallbackDcUserId": %d
                                }
                                """.formatted(userA.getId(), fallbackDc.getId())))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/users/bulk-delete")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userIds": [%d]
                                }
                                """.formatted(userA.getId())))
                .andExpect(status().isOk());

        User deleted = userRepository.findById(userA.getId()).orElseThrow();
        assertThat(deleted.getIsDeleted()).isTrue();
        assertThat(deleted.getEmail()).isNull();
    }

    @Test
    void bulkUserOperationsRequireAdminRole() throws Exception {
        String password = "BulkAuth123";
        User dc = createUser("DC", "bulk-auth-dc-", password);
        User fallbackDc = createUser("DC", "bulk-auth-fallback-", password);
        User target = createUser("DC", "bulk-auth-target-", password);

        String dcToken = loginAndGetToken(dc.getUsername(), password);

        mockMvc.perform(post("/api/admin/users/bulk-deactivate")
                        .header("Authorization", bearer(dcToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userIds": [%d],
                                  "fallbackDcUserId": %d
                                }
                                """.formatted(target.getId(), fallbackDc.getId())))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/users/bulk-delete")
                        .header("Authorization", bearer(dcToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userIds": [%d]
                                }
                                """.formatted(target.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void auditLogSearchFiltersByDocumentReferenceNumericIdActionTypeAndPerformer() throws Exception {
        String password = "AuditSearch123";
        User admin = createUser("ADMIN", "audit-search-admin-", password);
        User dc = createUser("DC", "audit-search-dc-", password);
        User ddc = createUser("DDC", "audit-search-ddc-", password);

        String adminToken = loginAndGetToken(admin.getUsername(), password);
        String dcToken = loginAndGetToken(dc.getUsername(), password);

        String uniqueRef = "AUDITREF-" + UUID.randomUUID().toString().substring(0, 8);
        long documentId = createDocumentWithExactRef(dcToken, uniqueRef);
        forwardDocument(documentId, dcToken, ddc.getId(), "PRIVATE", "forward for audit search");

        // A second document with a different reference must NOT match the ref filter below.
        createDocumentWithExactRef(dcToken, "OTHERREF-" + UUID.randomUUID().toString().substring(0, 8));

        // 1) Free-text document reference filter (exercises the join from audit log to documents).
        JsonNode byRef = readJson(mockMvc.perform(get("/api/audit-logs")
                        .param("document", uniqueRef)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(byRef.get("content")).isNotEmpty();
        for (JsonNode log : byRef.get("content")) {
            assertThat(log.get("entityId").asLong()).isEqualTo(documentId);
        }

        // 2) Numeric documentId filter returns the document's logs including the FORWARD movement.
        // A numeric value is also matched against reference numbers, so documents whose ref contains
        // those digits may also appear — assert our document's logs are present, not exclusivity.
        JsonNode byId = readJson(mockMvc.perform(get("/api/audit-logs")
                        .param("document", String.valueOf(documentId))
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn());
        boolean hasForward = false;
        boolean hasOurDocumentLog = false;
        for (JsonNode log : byId.get("content")) {
            if (log.get("entityId").asLong() == documentId) {
                hasOurDocumentLog = true;
                if ("FORWARD".equals(log.get("actionType").asText())) hasForward = true;
            }
        }
        assertThat(hasOurDocumentLog).as("documentId filter should include our document's logs").isTrue();
        assertThat(hasForward).as("documentId filter should include the FORWARD movement log").isTrue();

        // 3) Action-type filter narrows to a single action.
        JsonNode byAction = readJson(mockMvc.perform(get("/api/audit-logs")
                        .param("document", String.valueOf(documentId))
                        .param("actionType", "FORWARD")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(byAction.get("content")).isNotEmpty();
        for (JsonNode log : byAction.get("content")) {
            assertThat(log.get("actionType").asText()).isEqualTo("FORWARD");
        }

        // 4) Performer filter returns only that user's actions.
        JsonNode byPerformer = readJson(mockMvc.perform(get("/api/audit-logs")
                        .param("document", String.valueOf(documentId))
                        .param("performedByUserId", String.valueOf(dc.getId()))
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn());
        for (JsonNode log : byPerformer.get("content")) {
            assertThat(log.get("performedByUserId").asLong()).isEqualTo(dc.getId());
        }
    }

    private long createDocumentWithExactRef(String token, String refNo) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/documents")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refNo": "%s",
                                  "title": "Audit Search Document",
                                  "receivedDate": "%s",
                                  "companyName": "Integration Co",
                                  "priority": "HIGH",
                                  "documentType": "INTERNAL"
                                }
                                """.formatted(refNo, LocalDate.now().toString())))
                .andExpect(status().isCreated())
                .andReturn();
        return readJson(result).get("id").asLong();
    }

    @Test
    void adminUserCsvExportReturnsHeaderAndUserRowsAndRejectsNonAdmin() throws Exception {
        String password = "UserCsv123";
        User admin = createUser("ADMIN", "user-csv-admin-", password);
        User dc = createUser("DC", "user-csv-dc-", password);
        String adminToken = loginAndGetToken(admin.getUsername(), password);
        String dcToken = loginAndGetToken(dc.getUsername(), password);

        MvcResult result = mockMvc.perform(get("/api/admin/users/export")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getHeader("Content-Disposition")).contains("users-export.csv");
        String body = result.getResponse().getContentAsString();
        assertThat(body).startsWith("id,fullName,username,email,phone,department,role,active,createdAt");
        assertThat(body.lines().count()).isGreaterThan(1);
        assertThat(body).contains(dc.getUsername());

        // The export exposes every user's contact details, so it must stay admin-only.
        mockMvc.perform(get("/api/admin/users/export")
                        .header("Authorization", bearer(dcToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void myWorkloadStatsCountsAssignedAndOpenedDocumentsForOwner() throws Exception {
        String password = "Workload123";
        User dc = createUser("DC", "workload-dc-", password);
        User ddc = createUser("DDC", "workload-ddc-", password);

        String dcToken = loginAndGetToken(dc.getUsername(), password);
        String ddcToken = loginAndGetToken(ddc.getUsername(), password);

        long documentId = createDocument(dc, dcToken, "workload-doc");
        forwardDocument(documentId, dcToken, ddc.getId(), "PRIVATE", "assign to ddc");

        // Assigned to ddc but not yet opened by ddc.
        JsonNode before = readJson(mockMvc.perform(get("/api/documents/my-workload-stats")
                        .header("Authorization", bearer(ddcToken)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(before.get("assignedCount").asLong()).isEqualTo(1);
        assertThat(before.get("openedCount").asLong()).isEqualTo(0);
        assertThat(before.get("unopenedCount").asLong()).isEqualTo(1);

        // Opening the document records a view, which moves it from unopened to opened.
        mockMvc.perform(get("/api/documents/{id}", documentId)
                        .header("Authorization", bearer(ddcToken)))
                .andExpect(status().isOk());

        JsonNode after = readJson(mockMvc.perform(get("/api/documents/my-workload-stats")
                        .header("Authorization", bearer(ddcToken)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(after.get("assignedCount").asLong()).isEqualTo(1);
        assertThat(after.get("openedCount").asLong()).isEqualTo(1);
        assertThat(after.get("unopenedCount").asLong()).isEqualTo(0);
    }

    @Test
    void auditLogFilterOptionsReturnActionTypesAndPerformers() throws Exception {
        String password = "FilterOpts123";
        User admin = createUser("ADMIN", "filter-opts-admin-", password);
        User dc = createUser("DC", "filter-opts-dc-", password);
        String adminToken = loginAndGetToken(admin.getUsername(), password);
        String dcToken = loginAndGetToken(dc.getUsername(), password);

        // Generate audit activity: a CREATE action performed by dc.
        createDocument(dc, dcToken, "filter-opts-doc");

        JsonNode options = readJson(mockMvc.perform(get("/api/audit-logs/filter-options")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(options.get("actionTypes").isArray()).isTrue();
        boolean hasCreate = false;
        for (JsonNode a : options.get("actionTypes")) {
            if ("CREATE".equals(a.asText())) hasCreate = true;
        }
        assertThat(hasCreate).as("action types should include CREATE after a document is created").isTrue();

        assertThat(options.get("performers").isArray()).isTrue();
        boolean hasDc = false;
        for (JsonNode p : options.get("performers")) {
            if (p.get("id").asLong() == dc.getId()) hasDc = true;
        }
        assertThat(hasDc).as("performers should include the user who created the document").isTrue();
    }

    @Test
    void createdDocumentDetailReportsNoSendToUndoNotAlreadyMoved() throws Exception {
        String password = "UndoCreate123";
        User dc = createUser("DC", "undo-create-dc-", password);
        String dcToken = loginAndGetToken(dc.getUsername(), password);

        long documentId = createDocument(dc, dcToken, "undo-create-doc");

        MvcResult result = mockMvc.perform(get("/api/documents/{id}", documentId)
                        .header("Authorization", bearer(dcToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canUndoSend").value(false))
                .andReturn();

        // A just-created document was never sent, so the detail must not claim it "already moved".
        // It should report NO_SENT_MOVEMENT, which the UI renders as no undo-send notice at all.
        String undoStatus = readJson(result).path("undoSendStatus").asText("");
        assertThat(undoStatus).isEqualTo("NO_SENT_MOVEMENT");
    }

    @Test
    void returnAlwaysGoesToTheActualSenderIgnoringRequestedTarget() throws Exception {
        String password = "ReturnTarget123";
        User sender = createUser("DC", "return-sender-", password);
        User recipient = createUser("DDC", "return-recipient-", password);
        User stranger = createUser("DDC", "return-stranger-", password);

        String senderToken = loginAndGetToken(sender.getUsername(), password);
        String recipientToken = loginAndGetToken(recipient.getUsername(), password);

        long documentId = createDocument(sender, senderToken, "return-target");
        forwardDocument(documentId, senderToken, recipient.getId(), "PRIVATE", "forward for return target test");

        // The recipient asks to return to an arbitrary stranger who never sent the document; the backend
        // must ignore that and send it back to the actual sender instead.
        mockMvc.perform(post("/api/documents/{id}/return", documentId)
                        .header("Authorization", bearer(recipientToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "toUserId": %d
                                }
                                """.formatted(stranger.getId())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/documents/{id}", documentId)
                        .header("Authorization", bearer(senderToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentOwnerUserId").value(sender.getId()));
    }

    @Test
    void returnIsRejectedForADocumentThatWasNeverSentToYou() throws Exception {
        String password = "ReturnNone123";
        User dc = createUser("DC", "return-none-", password);
        String dcToken = loginAndGetToken(dc.getUsername(), password);

        long documentId = createDocument(dc, dcToken, "return-none");

        // dc created the document and never received it from anyone, so there is no sender to return to.
        mockMvc.perform(post("/api/documents/{id}/return", documentId)
                        .header("Authorization", bearer(dcToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "toUserId": %d
                                }
                                """.formatted(dc.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This document was not sent to you, so it cannot be returned."));
    }

    private User createUser(String roleName, String prefix, String rawPassword) {
        Role role = roleRepository.findByRoleName(roleName)
                .orElseThrow(() -> new IllegalStateException("Role not found: " + roleName));

        User user = new User();
        user.setFullName(prefix + "user");
        user.setUsername(prefix + UUID.randomUUID());
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        user.setIsActive(true);
        return userRepository.saveAndFlush(user);
    }

    private long createDocument(User user, String token, String refPrefix) throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/documents")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(documentPayload(refPrefix, "Admin Management Test Document", LocalDate.now().toString(), "HIGH")))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = readJson(createResult);
        assertThat(json.get("createdByUserId").asLong()).isEqualTo(user.getId());
        return json.get("id").asLong();
    }

    private String documentPayload(String refPrefix, String title, String receivedDate, String priority) {
        return """
                {
                  "refNo": "%s-%s",
                  "title": "%s",
                  "receivedDate": "%s",
                  "companyName": "Integration Co",
                  "priority": "%s",
                  "documentType": "INTERNAL"
                }
                """.formatted(refPrefix, UUID.randomUUID(), title, receivedDate, priority);
    }

    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String loginAndGetToken(String username, String password) throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "%s"
                                }
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn();

        return readJson(loginResult).get("accessToken").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private void updatePermission(String adminToken, String roleName, AppPermission permission, boolean enabled) throws Exception {
        mockMvc.perform(put("/api/admin/permissions")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "entries": [
                                    {
                                      "roleName": "%s",
                                      "permission": "%s",
                                      "enabled": %s
                                    }
                                  ]
                                }
                                """.formatted(roleName, permission.name(), enabled)))
                .andExpect(status().isOk());
    }

    private void restorePermission(Role role, AppPermission permission, boolean enabled) {
        rolePermissionRepository.findByRole_IdAndPermissionNameIgnoreCase(role.getId(), permission.name())
                .ifPresent(existing -> {
                    existing.setEnabled(enabled);
                    rolePermissionRepository.saveAndFlush(existing);
                });
    }

    private Role requireRole(String roleName) {
        return roleRepository.findByRoleName(roleName)
                .orElseThrow(() -> new IllegalStateException("Role not found: " + roleName));
    }

    private boolean readPermissionEnabled(Role role, AppPermission permission) {
        return rolePermissionRepository.findByRole_IdAndPermissionNameIgnoreCase(role.getId(), permission.name())
                .map(entry -> Boolean.TRUE.equals(entry.getEnabled()))
                .orElseThrow(() -> new IllegalStateException("Permission not found for " + role.getRoleName() + " " + permission.name()));
    }

    private long createOwnedDocumentForActor(User admin, String adminToken, long actorUserId, String refPrefix) throws Exception {
        long documentId = createDocument(admin, adminToken, refPrefix);
        forwardDocument(documentId, adminToken, actorUserId, "PRIVATE", "Admin assigned workflow setup document");
        return documentId;
    }

    private long createWorkflowOwnedDocument(User admin, String adminToken, User actor, String actorToken, String refPrefix) throws Exception {
        if (canCreateDocuments(actor)) {
            return createDocument(actor, actorToken, refPrefix);
        }
        return createOwnedDocumentForActor(admin, adminToken, actor.getId(), refPrefix);
    }

    private void forwardToAdmin(long documentId, String actorToken, long adminUserId, String visibility) throws Exception {
        forwardDocument(documentId, actorToken, adminUserId, visibility, "Workflow setup forward");
    }

    private void forwardDocument(long documentId, String token, long toUserId, String visibility, String remarkText) throws Exception {
        mockMvc.perform(post("/api/documents/{id}/forward", documentId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "toUserId": %d,
                                  "forwardVisibility": "%s",
                                  "remarkText": "%s"
                                }
                                """.formatted(toUserId, visibility, remarkText)))
                .andExpect(status().isOk());
    }

    private void forwardDocumentAsAdmin(long documentId, String adminToken, long toUserId) throws Exception {
        forwardDocument(documentId, adminToken, toUserId, "PRIVATE", "Admin forwarded back");
    }

    private void approveDocumentAsActor(long documentId, String actorToken) throws Exception {
        mockMvc.perform(post("/api/documents/{id}/approve", documentId)
                        .header("Authorization", bearer(actorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remarkText\":\"Setup approve\"}"))
                .andExpect(status().isOk());
    }

    private void rejectDocumentAsActor(long documentId, String actorToken) throws Exception {
        mockMvc.perform(post("/api/documents/{id}/reject", documentId)
                        .header("Authorization", bearer(actorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remarkText\":\"Setup reject\"}"))
                .andExpect(status().isOk());
    }

    private boolean canCreateDocuments(User actor) {
        String roleName = actor.getRole() == null ? null : actor.getRole().getRoleName();
        return "ADMIN".equals(roleName) || "DC".equals(roleName) || "PMA".equals(roleName);
    }

    private static Stream<String> allRoleNames() {
        return Stream.of("ADMIN", "DC", "DDC", "SDDC", "SC", "ASC", "PMA");
    }

    private static Set<AppPermission> expectedEnabledPermissionsForRole(String roleName) {
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

        return switch (roleName) {
            case "ADMIN" -> EnumSet.allOf(AppPermission.class);
            case "DC" -> EnumSet.of(
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
            );
            case "DDC", "SDDC", "SC", "ASC" -> EnumSet.copyOf(allWorkflow);
            case "PMA" -> EnumSet.of(
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
            );
            default -> throw new IllegalArgumentException("Unknown role: " + roleName);
        };
    }
}
