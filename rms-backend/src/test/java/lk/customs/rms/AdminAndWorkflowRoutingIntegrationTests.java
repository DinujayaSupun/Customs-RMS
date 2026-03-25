package lk.customs.rms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lk.customs.rms.entity.Role;
import lk.customs.rms.entity.User;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class AdminAndWorkflowRoutingIntegrationTests {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

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
    void adminEndpointsRequireAdminRoleButAdminCanAccessThem() throws Exception {
        String password = "Admin1234";
        User admin = createUser("ADMIN", "admin-access-", password);
        User sc = createUser("SC", "admin-denied-", password);

        String adminToken = loginAndGetToken(admin.getUsername(), password);
        String scToken = loginAndGetToken(sc.getUsername(), password);

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", bearer(scToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/permissions")
                        .header("Authorization", bearer(scToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        mockMvc.perform(get("/api/admin/permissions")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").isArray())
                .andExpect(jsonPath("$.permissions").isArray())
                .andExpect(jsonPath("$.entries").isArray());
    }

    @Test
    void currentOwnerCanForwardAndRecipientCanReturnDocument() throws Exception {
        String password = "Flow1234";
        User dc = createUser("DC", "route-dc-", password);
        User ddc = createUser("DDC", "route-ddc-", password);

        String dcToken = loginAndGetToken(dc.getUsername(), password);
        String ddcToken = loginAndGetToken(ddc.getUsername(), password);

        long documentId = createDocument(dc, dcToken, "forward-return");

        mockMvc.perform(post("/api/documents/{id}/forward", documentId)
                        .header("Authorization", bearer(dcToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "toUserId": %d,
                                  "forwardVisibility": "PRIVATE",
                                  "remarkText": "Forwarded to DDC in test"
                                }
                                """.formatted(ddc.getId())))
                .andExpect(status().isOk());

        MvcResult forwarded = mockMvc.perform(get("/api/documents/{id}", documentId)
                        .header("Authorization", bearer(ddcToken)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode forwardedJson = readJson(forwarded);
        assertThat(forwardedJson.get("currentOwnerUserId").asLong()).isEqualTo(ddc.getId());
        assertThat(forwardedJson.get("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(forwardedJson.get("visibility").asText()).isEqualTo("PRIVATE");

        mockMvc.perform(post("/api/documents/{id}/return", documentId)
                        .header("Authorization", bearer(ddcToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "toUserId": %d,
                                  "remarkText": "Returned to DC in test"
                                }
                                """.formatted(dc.getId())))
                .andExpect(status().isOk());

        MvcResult returned = mockMvc.perform(get("/api/documents/{id}", documentId)
                        .header("Authorization", bearer(dcToken)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode returnedJson = readJson(returned);
        assertThat(returnedJson.get("currentOwnerUserId").asLong()).isEqualTo(dc.getId());
        assertThat(returnedJson.get("status").asText()).isEqualTo("RETURNED");
    }

    @Test
    void previousOwnerCannotActAfterForwardingAwayDocument() throws Exception {
        String password = "Flow1234";
        User dc = createUser("DC", "owner-dc-", password);
        User ddc = createUser("DDC", "owner-ddc-", password);

        String dcToken = loginAndGetToken(dc.getUsername(), password);
        long documentId = createDocument(dc, dcToken, "owner-check");

        mockMvc.perform(post("/api/documents/{id}/forward", documentId)
                        .header("Authorization", bearer(dcToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "toUserId": %d,
                                  "forwardVisibility": "PUBLIC",
                                  "remarkText": "Handing off ownership"
                                }
                                """.formatted(ddc.getId())))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/documents/{id}/approve", documentId)
                        .header("Authorization", bearer(dcToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "remarkText": "Former owner should not approve"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only the current owner can approve this document."));
    }

    @Test
    void unrelatedUserCannotOpenPrivateDocumentForwardedBetweenOthers() throws Exception {
        String password = "View1234";
        User dc = createUser("DC", "private-dc-", password);
        User ddc = createUser("DDC", "private-ddc-", password);
        User outsider = createUser("SC", "private-outsider-", password);

        String dcToken = loginAndGetToken(dc.getUsername(), password);
        String outsiderToken = loginAndGetToken(outsider.getUsername(), password);

        long documentId = createDocument(dc, dcToken, "private-view");

        mockMvc.perform(post("/api/documents/{id}/forward", documentId)
                        .header("Authorization", bearer(dcToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "toUserId": %d,
                                  "forwardVisibility": "PRIVATE",
                                  "remarkText": "Private routing test"
                                }
                                """.formatted(ddc.getId())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/documents/{id}", documentId)
                        .header("Authorization", bearer(outsiderToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You are not allowed to view this private document."));
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
                        .content(documentPayload(refPrefix, "Routing Test Document", LocalDate.now().toString(), "HIGH")))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = readJson(createResult);
        assertThat(json.get("createdByUserId").asLong()).isEqualTo(user.getId());
        assertThat(json.get("currentOwnerUserId").asLong()).isEqualTo(user.getId());
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
