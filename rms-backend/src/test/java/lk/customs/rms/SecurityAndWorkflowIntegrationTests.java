package lk.customs.rms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lk.customs.rms.entity.DcAutoForwardConfig;
import lk.customs.rms.entity.Role;
import lk.customs.rms.entity.User;
import lk.customs.rms.repository.DcAutoForwardConfigRepository;
import lk.customs.rms.repository.RoleRepository;
import lk.customs.rms.repository.UserRepository;
import lk.customs.rms.security.NotificationHandshakeInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    @Autowired
    private NotificationHandshakeInterceptor notificationHandshakeInterceptor;

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
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(admin.getUsername()));
    }

    @Test
    void profilePictureDownloadUsesScopedDownloadTokenAndRejectsAccessTokenQuery() throws Exception {
        String password = "Query1234";
        User user = createUser("ADMIN", "query-profile-", password);
        String token = loginAndGetToken(user.getUsername(), password);

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

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasProfilePicture").value(true));

        mockMvc.perform(get("/api/auth/me/profile-picture")
                        .param("access_token", token))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/auth/me/profile-picture")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentType()).isEqualTo(MediaType.IMAGE_PNG_VALUE));

        MvcResult tokenResult = mockMvc.perform(post("/api/auth/me/profile-picture-token")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.containsString("download_token=")))
                .andExpect(jsonPath("$.expiresInSeconds").value(120))
                .andReturn();

        String downloadToken = queryParam(readJson(tokenResult).get("url").asText(), "download_token");
        assertThat(downloadToken).isNotBlank();

        mockMvc.perform(get("/api/auth/me/profile-picture")
                        .param("download_token", downloadToken))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentType()).isEqualTo(MediaType.IMAGE_PNG_VALUE));
    }

    @Test
    void scopedDownloadTokenIsRejectedAsBearerAccessToken() throws Exception {
        String password = "Bearer1234";
        User user = createUser("ADMIN", "download-as-bearer-", password);
        String accessToken = loginAndGetToken(user.getUsername(), password);

        // A profile picture must exist before a download token can be minted for it.
        mockMvc.perform(multipart("/api/auth/me/profile-picture")
                        .file(new MockMultipartFile("file", "avatar.png", MediaType.IMAGE_PNG_VALUE, pngBytes()))
                        .header("Authorization", bearer(accessToken)))
                .andExpect(status().isOk());

        MvcResult tokenResult = mockMvc.perform(post("/api/auth/me/profile-picture-token")
                        .header("Authorization", bearer(accessToken)))
                .andExpect(status().isOk())
                .andReturn();
        String downloadToken = queryParam(readJson(tokenResult).get("url").asText(), "download_token");
        assertThat(downloadToken).isNotBlank();

        // The scoped download token carries the user's identity but must NOT be usable as a
        // general Bearer access token: it may only authenticate via the narrow download path.
        // (Regression guard: previously isTokenValid ignored token_type, so this returned 200.)
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", bearer(downloadToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void scopedDownloadTokenIsRejectedAtWebSocketHandshake() throws Exception {
        String password = "Bearer1234";
        User user = createUser("ADMIN", "download-as-ws-", password);
        String accessToken = loginAndGetToken(user.getUsername(), password);

        mockMvc.perform(multipart("/api/auth/me/profile-picture")
                        .file(new MockMultipartFile("file", "avatar.png", MediaType.IMAGE_PNG_VALUE, pngBytes()))
                        .header("Authorization", bearer(accessToken)))
                .andExpect(status().isOk());

        MvcResult tokenResult = mockMvc.perform(post("/api/auth/me/profile-picture-token")
                        .header("Authorization", bearer(accessToken)))
                .andExpect(status().isOk())
                .andReturn();
        String downloadToken = queryParam(readJson(tokenResult).get("url").asText(), "download_token");
        assertThat(downloadToken).isNotBlank();

        // A scoped download token must not be accepted as the WebSocket handshake's auth token
        // either - the same rule already enforced for the HTTP Bearer path.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ws/notifications");
        request.setParameter("token", downloadToken);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean handshakeAllowed = notificationHandshakeInterceptor.beforeHandshake(
                new ServletServerHttpRequest(request),
                new ServletServerHttpResponse(response),
                null,
                new HashMap<>()
        );

        assertThat(handshakeAllowed).as("handshake must reject a scoped download token").isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void queryTokenIsRejectedForGeneralDocumentRoutesAndNonGetRequests() throws Exception {
        String password = "Query1234";
        User admin = createUser("ADMIN", "query-block-", password);
        String token = loginAndGetToken(admin.getUsername(), password);

        mockMvc.perform(get("/api/documents")
                        .param("access_token", token))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/documents")
                        .param("access_token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(documentPayload("blocked-by-query-token", "Blocked", LocalDate.now().toString(), "LOW")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createDocumentPersistsDocumentTypeAndRequiresIt() throws Exception {
        String password = "DocType1234";
        User admin = createUser("ADMIN", "doc-type-", password);
        String token = loginAndGetToken(admin.getUsername(), password);

        // An explicit type is persisted and echoed back in the response.
        String externalDoc = """
                {
                  "refNo": "doc-type-ext-%s",
                  "title": "External doc",
                  "receivedDate": "%s",
                  "companyName": "Integration Co",
                  "priority": "HIGH",
                  "documentType": "EXTERNAL"
                }
                """.formatted(UUID.randomUUID(), LocalDate.now());
        mockMvc.perform(post("/api/documents")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(externalDoc))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentType").value("EXTERNAL"));

        // documentType is required: omitting it fails validation with 400.
        String missingType = """
                {
                  "refNo": "doc-type-missing-%s",
                  "title": "No type",
                  "receivedDate": "%s",
                  "companyName": "Integration Co",
                  "priority": "LOW"
                }
                """.formatted(UUID.randomUUID(), LocalDate.now());
        mockMvc.perform(post("/api/documents")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(missingType))
                .andExpect(status().isBadRequest());
    }

    @Test
    void repeatedFailedLoginsFromSameIpAreThrottledWith429() throws Exception {
        String password = "Throttle1234";
        User user = createUser("ADMIN", "throttle-", password);
        // Unique source IP so this test's failures do not pollute the default address other tests share.
        String sourceIp = "203.0.113.77";

        // The throttle allows 10 failed attempts; each returns 400 (invalid credentials).
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .header("X-Forwarded-For", sourceIp)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "username": "%s",
                                      "password": "WrongPassword%d"
                                    }
                                    """.formatted(user.getUsername(), i)))
                    .andExpect(status().isBadRequest());
        }

        // The 11th request from the same IP is blocked with 429 BEFORE credentials are checked —
        // even the correct password is rejected while the source is throttled.
        mockMvc.perform(post("/api/auth/login")
                        .header("X-Forwarded-For", sourceIp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "%s"
                                }
                                """.formatted(user.getUsername(), password)))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void createDocumentRejectsMissingRequiredFieldsWithValidationDetails() throws Exception {
        String password = "Validate1234";
        User admin = createUser("ADMIN", "validation-admin-", password);
        String token = loginAndGetToken(admin.getUsername(), password);

        mockMvc.perform(post("/api/documents")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refNo": "   ",
                                  "title": "",
                                  "companyName": "Integration Co"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid request"))
                .andExpect(jsonPath("$.details", hasItems(
                        "refNo: must not be blank",
                        "title: must not be blank",
                        "receivedDate: must not be null",
                        "priority: must not be null"
                )));
    }

    @Test
    void createDocumentRejectsInvalidPriorityAsBadRequest() throws Exception {
        String password = "Validate1234";
        User admin = createUser("ADMIN", "invalid-priority-admin-", password);
        String token = loginAndGetToken(admin.getUsername(), password);

        mockMvc.perform(post("/api/documents")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(documentPayload("invalid-priority", "Invalid Priority", LocalDate.now().toString(), "CRITICAL")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid enum value provided."));
    }

    @Test
    void updateMyProfileTrimsFieldsAndRejectsBlankFullName() throws Exception {
        String password = "Profile1234";
        User user = createUser("SC", "profile-update-", password);
        String token = loginAndGetToken(user.getUsername(), password);

        mockMvc.perform(put("/api/auth/me")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "   ",
                                  "email": "ignored@example.com",
                                  "phone": "0771111111"
                                }
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid request"))
                .andExpect(jsonPath("$.details", hasItems("fullName: Full name is required.")));

        mockMvc.perform(put("/api/auth/me")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "  Samantha Tester  ",
                                  "email": "samantha@example.com",
                                  "phone": null
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Samantha Tester"))
                .andExpect(jsonPath("$.email").value("samantha@example.com"))
                .andExpect(jsonPath("$.phone").doesNotExist());
    }

    @Test
    void changeMyPasswordValidatesCurrentMatchConfirmationAndDifferentPassword() throws Exception {
        String password = "Password1234";
        User user = createUser("SC", "password-change-", password);
        String token = loginAndGetToken(user.getUsername(), password);

        mockMvc.perform(patch("/api/auth/me/password")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "Wrong1234",
                                  "newPassword": "NewPassword1234",
                                  "confirmPassword": "NewPassword1234"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Current password is incorrect."));

        mockMvc.perform(patch("/api/auth/me/password")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "Password1234",
                                  "newPassword": "NewPassword1234",
                                  "confirmPassword": "Mismatch1234"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("New password and confirm password must match."));

        mockMvc.perform(patch("/api/auth/me/password")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "Password1234",
                                  "newPassword": "Password1234",
                                  "confirmPassword": "Password1234"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("New password must be different from current password."));

        mockMvc.perform(patch("/api/auth/me/password")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "Password1234",
                                  "newPassword": "Changed1234",
                                  "confirmPassword": "Changed1234"
                                }
                                """))
                .andExpect(status().isNoContent());

        loginAndGetToken(user.getUsername(), "Changed1234");
    }

    @Test
    void profilePictureUploadRejectsEmptyLargeAndUnsupportedFiles() throws Exception {
        String password = "Picture1234";
        User user = createUser("SC", "picture-validation-", password);
        String token = loginAndGetToken(user.getUsername(), password);

        mockMvc.perform(multipart("/api/auth/me/profile-picture")
                        .file(new MockMultipartFile("file", "empty.png", MediaType.IMAGE_PNG_VALUE, new byte[0]))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Profile picture file is required."));

        mockMvc.perform(multipart("/api/auth/me/profile-picture")
                        .file(new MockMultipartFile("file", "avatar.txt", MediaType.TEXT_PLAIN_VALUE, "not image".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only JPG, PNG, or WEBP profile pictures are allowed."));

        byte[] tooLarge = new byte[(5 * 1024 * 1024) + 1];
        mockMvc.perform(multipart("/api/auth/me/profile-picture")
                        .file(new MockMultipartFile("file", "large.png", MediaType.IMAGE_PNG_VALUE, tooLarge))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Profile picture must be 5MB or smaller."));
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

    @Test
    void pageSizeConstraintRejectsValuesOver500() throws Exception {
        String password = "PageSize123";
        User user = createUser("ADMIN", "page-size-", password);
        String token = loginAndGetToken(user.getUsername(), password);

        mockMvc.perform(get("/api/documents").param("size", "501")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/documents/my-inbox").param("size", "501")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/documents/sent-messages").param("size", "501")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/documents").param("size", "500")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
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

    private String queryParam(String url, String name) throws Exception {
        String query = new URI(url).getRawQuery();
        assertThat(query).isNotBlank();

        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2 && name.equals(parts[0])) {
                return parts[1];
            }
        }

        throw new IllegalStateException("Query parameter not found: " + name);
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
