package lk.customs.rms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lk.customs.rms.entity.Role;
import lk.customs.rms.entity.RolePermission;
import lk.customs.rms.entity.User;
import lk.customs.rms.repository.RolePermissionRepository;
import lk.customs.rms.repository.RoleRepository;
import lk.customs.rms.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
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
    void adminCanDeactivateUserTransferOwnershipAndResetPassword() throws Exception {
        String adminPassword = "Admin1234";
        String userPassword = "Member1234";
        String resetPassword = "Reset5678";

        User admin = createUser("ADMIN", "user-admin-", adminPassword);
        User fallbackDc = createUser("DC", "fallback-dc-", userPassword);
        User scUser = createUser("SC", "deactivate-sc-", userPassword);

        String adminToken = loginAndGetToken(admin.getUsername(), adminPassword);
        String scToken = loginAndGetToken(scUser.getUsername(), userPassword);

        long documentId = createDocument(scUser, scToken, "deactivate-transfer");

        mockMvc.perform(patch("/api/admin/users/{userId}/deactivate", scUser.getId())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fallbackDcUserId": %d
                                }
                                """.formatted(fallbackDc.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(scUser.getId()))
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "%s"
                                }
                                """.formatted(scUser.getUsername(), userPassword)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid username or password."));

        mockMvc.perform(get("/api/documents/{id}", documentId)
                        .header("Authorization", bearer(loginAndGetToken(fallbackDc.getUsername(), userPassword))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentOwnerUserId").value(fallbackDc.getId()));

        mockMvc.perform(patch("/api/admin/users/{userId}/reset-password", scUser.getId())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "newPassword": "%s"
                                }
                                """.formatted(resetPassword)))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/admin/users/{userId}/activate", scUser.getId())
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
                                """.formatted(scUser.getUsername(), resetPassword)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(scUser.getUsername()));
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
                  "priority": "%s"
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
}
