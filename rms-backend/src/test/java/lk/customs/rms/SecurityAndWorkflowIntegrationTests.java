package lk.customs.rms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lk.customs.rms.entity.DcAutoForwardConfig;
import lk.customs.rms.entity.Role;
import lk.customs.rms.entity.User;
import lk.customs.rms.repository.DcAutoForwardConfigRepository;
import lk.customs.rms.repository.RoleRepository;
import lk.customs.rms.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class SecurityAndWorkflowIntegrationTests {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private DcAutoForwardConfigRepository dcAutoForwardConfigRepository;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        ensureApproveRejectButtonsEnabled();
    }

    @Test
    void loginSupportsSuccessAndRejectsInvalidCredentials() throws Exception {
        String password = "Login1234";
        User admin = createUser("ADMIN", "login-admin-", password);
        String token = loginAndGetToken(admin.getUsername(), password);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "%s"
                                }
                                """.formatted(admin.getUsername(), password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(admin.getUsername()))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "Wrong1234"
                                }
                                """.formatted(admin.getUsername())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid username or password."));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "unknown-user",
                                  "password": "Wrong1234"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid username or password."));

        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(admin.getUsername()));
    }

    @Test
    void queryTokenIsAllowedForProfilePictureAndAttachmentDownload() throws Exception {
        String password = "Query1234";
        User admin = createUser("ADMIN", "query-allow-", password);
        String token = loginAndGetToken(admin.getUsername(), password);

        MockMultipartFile profilePicture = new MockMultipartFile(
                "file",
                "avatar.png",
                MediaType.IMAGE_PNG_VALUE,
                pngBytes()
        );

        mockMvc.perform(multipart("/api/auth/me/profile-picture")
                        .file(profilePicture)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasProfilePicture").value(true));

        mockMvc.perform(get("/api/auth/me/profile-picture")
                        .param("access_token", token))
                .andExpect(status().isOk());

        long documentId = createDocument(admin, token, "query-allow-doc");

        MockMultipartFile attachment = new MockMultipartFile(
                "file",
                "sample.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "attachment-body".getBytes(StandardCharsets.UTF_8)
        );

        MvcResult uploadResult = mockMvc.perform(multipart("/api/documents/{documentId}/attachments", documentId)
                        .file(attachment)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn();

        long attachmentId = readJson(uploadResult).get("id").asLong();

        mockMvc.perform(get("/api/attachments/{attachmentId}/download", attachmentId)
                        .param("access_token", token))
                .andExpect(status().isOk());
    }

    @Test
    void queryTokenIsRejectedForGeneralDocumentRoutesAndNonGetRequests() throws Exception {
        String password = "Query1234";
        User admin = createUser("ADMIN", "query-block-", password);
        String token = loginAndGetToken(admin.getUsername(), password);

        mockMvc.perform(get("/api/documents")
                        .param("access_token", token))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/documents")
                        .param("access_token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(documentPayload("blocked-by-query-token", "Blocked", LocalDate.now().toString(), "LOW")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminOwnerCanCreateApproveAndIssueDocument() throws Exception {
        String password = "Flow1234";
        User admin = createUser("ADMIN", "workflow-admin-", password);
        String token = loginAndGetToken(admin.getUsername(), password);

        long documentId = createDocument(admin, token, "workflow-approve-issue");

        mockMvc.perform(post("/api/documents/{id}/approve", documentId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "remarkText": "Approved in test"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/documents/{id}/issue", documentId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "remarkText": "Issued in test"
                                }
                                """))
                .andExpect(status().isOk());

        MvcResult finalDoc = mockMvc.perform(get("/api/documents/{id}", documentId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(readJson(finalDoc).get("status").asText()).isEqualTo("ISSUED");
    }

    @Test
    void adminOwnerCanRejectAndReopenDocument() throws Exception {
        String password = "Flow1234";
        User admin = createUser("ADMIN", "workflow-reopen-", password);
        String token = loginAndGetToken(admin.getUsername(), password);

        long documentId = createDocument(admin, token, "workflow-reject-reopen");

        mockMvc.perform(post("/api/documents/{id}/reject", documentId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "remarkText": "Rejected in test"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/documents/{id}/reopen", documentId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "remarkText": "Reopened in test"
                                }
                                """))
                .andExpect(status().isOk());

        MvcResult finalDoc = mockMvc.perform(get("/api/documents/{id}", documentId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(readJson(finalDoc).get("status").asText()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void unauthorizedUserCannotDownloadAnotherUsersPrivateAttachment() throws Exception {
        String password = "Attach1234";
        User owner = createUser("ADMIN", "attach-owner-", password);
        User outsider = createUser("SC", "attach-outsider-", password);
        String ownerToken = loginAndGetToken(owner.getUsername(), password);
        String outsiderToken = loginAndGetToken(outsider.getUsername(), password);

        long documentId = createDocument(owner, ownerToken, "attachment-auth");

        MockMultipartFile attachment = new MockMultipartFile(
                "file",
                "private.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "private-body".getBytes(StandardCharsets.UTF_8)
        );

        MvcResult uploadResult = mockMvc.perform(multipart("/api/documents/{documentId}/attachments", documentId)
                        .file(attachment)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andReturn();

        long attachmentId = readJson(uploadResult).get("id").asLong();

        mockMvc.perform(get("/api/attachments/{attachmentId}/download", attachmentId)
                        .header("Authorization", bearer(outsiderToken)))
                .andExpect(status().isBadRequest());
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
                        .content(documentPayload(refPrefix, "Integration Test Document", LocalDate.now().toString(), "HIGH")))
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

    private void ensureApproveRejectButtonsEnabled() {
        DcAutoForwardConfig config = dcAutoForwardConfigRepository.findById(1L).orElseGet(DcAutoForwardConfig::new);
        config.setId(1L);
        config.setApproveRejectButtonsEnabled(true);
        config.setForwardReturnAllowedStatuses("PENDING,IN_PROGRESS,RETURNED");
        dcAutoForwardConfigRepository.saveAndFlush(config);
    }

    private byte[] pngBytes() {
        return java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+nXWQAAAAASUVORK5CYII=");
    }
}
