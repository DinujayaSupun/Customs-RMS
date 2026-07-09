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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice 3 of Feature 3 (groups): a group-held document must show up as *actionable* (canWorkflow=true)
 * for any group admin - not just the anchor (current_owner_user_id) - in both the inbox listing and
 * the single-document view, even when that admin's role has no CC_VIEW_DOCUMENT permission override.
 * Regular (non-admin) members remain CC-only (canWorkflow=false) in both places.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DocumentGroupInboxAndViewIntegrationTests {

    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
    }

    @Test
    void nonAnchorGroupAdminSeesGroupHeldDocumentAsActionableInInbox() throws Exception {
        String password = "Grp1234";
        User dc = createUser("DC", "gi-dc-", password);
        User groupCreatorAdmin = createUser("ADMIN", "gi-creator-", password);
        User adminX = createUser("DDC", "gi-adminx-", password);
        User groupMember = createUser("SC", "gi-member-", password);

        String dcToken = loginAndGetToken(dc.getUsername(), password);
        String creatorToken = loginAndGetToken(groupCreatorAdmin.getUsername(), password);
        String adminXToken = loginAndGetToken(adminX.getUsername(), password);
        String memberToken = loginAndGetToken(groupMember.getUsername(), password);

        long groupId = createGroup(creatorToken, "Inbox Group", adminX.getId(), true, groupMember.getId(), false);
        long documentId = createDocument(dcToken, "gi-inbox");
        forwardToGroup(dcToken, documentId, groupId, "PRIVATE").andExpect(status().isOk());

        // Determine which of {groupCreatorAdmin, adminX} is the anchor via dc's unconditional
        // creator-view access (independent of the group logic under test).
        long anchorId = getDocument(dcToken, documentId).get("currentOwnerUserId").asLong();
        String nonAnchorAdminToken = anchorId == adminX.getId() ? creatorToken : adminXToken;

        // The non-anchor admin has no CC_VIEW_DOCUMENT override and the doc is PRIVATE - the only
        // reason they should see it as actionable is group-admin membership.
        JsonNode inboxRow = findInboxRow(nonAnchorAdminToken, documentId);
        assertThat(inboxRow).as("non-anchor admin sees the group-held doc in their inbox").isNotNull();
        assertThat(inboxRow.get("canWorkflow").asBoolean()).as("non-anchor admin can act on it").isTrue();

        // A regular member also sees it, but strictly as CC (not actionable).
        JsonNode memberRow = findInboxRow(memberToken, documentId);
        assertThat(memberRow).as("member sees the group-held doc in their inbox").isNotNull();
        assertThat(memberRow.get("canWorkflow").asBoolean()).as("member cannot act on it").isFalse();
        assertThat(memberRow.get("recipientType").asText()).isEqualTo("CC");
    }

    @Test
    void nonAnchorGroupAdminCanViewGroupHeldPrivateDocumentDirectly() throws Exception {
        String password = "Grp1234";
        User dc = createUser("DC", "gv-dc-", password);
        User groupCreatorAdmin = createUser("ADMIN", "gv-creator-", password);
        User adminX = createUser("DDC", "gv-adminx-", password);

        String dcToken = loginAndGetToken(dc.getUsername(), password);
        String creatorToken = loginAndGetToken(groupCreatorAdmin.getUsername(), password);
        String adminXToken = loginAndGetToken(adminX.getUsername(), password);

        long groupId = createGroup(creatorToken, "Direct View Group", adminX.getId(), true);
        long documentId = createDocument(dcToken, "gv-direct");
        forwardToGroup(dcToken, documentId, groupId, "PRIVATE").andExpect(status().isOk());

        long anchorId = getDocument(dcToken, documentId).get("currentOwnerUserId").asLong();
        String nonAnchorAdminToken = anchorId == adminX.getId() ? creatorToken : adminXToken;

        JsonNode viewed = getDocument(nonAnchorAdminToken, documentId);
        assertThat(viewed.get("canWorkflow").asBoolean()).isTrue();
    }

    // ---- helpers ----

    private JsonNode findInboxRow(String token, long documentId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/documents/my-inbox").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode content = readJson(result).get("content");
        for (JsonNode row : content) {
            if (row.get("id").asLong() == documentId) {
                return row;
            }
        }
        return null;
    }

    private org.springframework.test.web.servlet.ResultActions forwardToGroup(
            String token, long documentId, long groupId, String visibility) throws Exception {
        return mockMvc.perform(post("/api/documents/{id}/forward", documentId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "toGroupId": %d, "forwardVisibility": "%s" }
                        """.formatted(groupId, visibility)));
    }

    private JsonNode getDocument(String token, long documentId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/documents/{id}", documentId).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        return readJson(result);
    }

    private long createGroup(String creatorToken, String name, Object... memberIdThenIsAdmin) throws Exception {
        StringBuilder members = new StringBuilder();
        for (int i = 0; i < memberIdThenIsAdmin.length; i += 2) {
            if (i > 0) members.append(",");
            members.append("{\"userId\":").append(memberIdThenIsAdmin[i])
                    .append(",\"isAdmin\":").append(memberIdThenIsAdmin[i + 1]).append("}");
        }
        MvcResult result = mockMvc.perform(post("/api/groups")
                        .header("Authorization", bearer(creatorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "%s", "color": "#123456", "members": [ %s ] }
                                """.formatted(name, members)))
                .andExpect(status().isCreated())
                .andReturn();
        return readJson(result).get("id").asLong();
    }

    private long createDocument(String token, String refPrefix) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/documents")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refNo": "REF-%s-%d",
                                  "title": "Group Inbox Test Document",
                                  "companyName": "Test Company",
                                  "receivedDate": "2026-06-16",
                                  "priority": "HIGH",
                                  "documentType": "INTERNAL"
                                }
                                """.formatted(refPrefix, System.currentTimeMillis())))
                .andExpect(status().isCreated())
                .andReturn();
        return readJson(result).get("id").asLong();
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

    private String loginAndGetToken(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        return readJson(result).get("accessToken").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
