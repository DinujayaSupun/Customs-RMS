package lk.customs.rms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lk.customs.rms.dto.RealtimeNotificationMessage;
import lk.customs.rms.entity.Role;
import lk.customs.rms.entity.User;
import lk.customs.rms.entity.UserPermission;
import lk.customs.rms.repository.RoleRepository;
import lk.customs.rms.repository.UserPermissionRepository;
import lk.customs.rms.repository.UserRepository;
import lk.customs.rms.websocket.NotificationWebSocketHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice 2 of Feature 3 (groups): a document forwarded to a group is held by the group - any group
 * admin can act on it (forward/approve/reject/issue/reopen/edit/remark), regular members get
 * CC-level view access only, and non-members get neither.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DocumentGroupForwardingIntegrationTests {

    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserPermissionRepository userPermissionRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private WebApplicationContext webApplicationContext;
    @MockitoSpyBean private NotificationWebSocketHandler notificationWebSocketHandler;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
    }

    @Test
    void nonAnchorGroupAdminCanActOnGroupHeldDocumentAndForwardOnward() throws Exception {
        String password = "Grp1234";
        User dc = createUser("DC", "gf-dc-", password);
        User groupOwnerAdmin = createUser("ADMIN", "gf-owner-", password); // creates the group only
        User admin1 = createUser("DDC", "gf-admin1-", password);
        User admin2 = createUser("SDDC", "gf-admin2-", password);
        User finalRecipient = createUser("PMA", "gf-final-", password);

        String dcToken = loginAndGetToken(dc.getUsername(), password);
        String admin1Token = loginAndGetToken(admin1.getUsername(), password);
        String admin2Token = loginAndGetToken(admin2.getUsername(), password);

        long groupId = createGroup(loginAndGetToken(groupOwnerAdmin.getUsername(), password),
                "Onward Group", admin1.getId(), true, admin2.getId(), true);

        long documentId = createDocument(dcToken, "gf-onward");
        forwardToGroup(dcToken, documentId, groupId).andExpect(status().isOk());

        JsonNode asAdmin1 = getDocument(admin1Token, documentId);
        JsonNode asAdmin2 = getDocument(admin2Token, documentId);
        assertThat(asAdmin1.get("canWorkflow").asBoolean()).as("admin1 can act").isTrue();
        assertThat(asAdmin2.get("canWorkflow").asBoolean()).as("admin2 can act").isTrue();

        // The anchor (current_owner_user_id) is whichever group admin has the lowest id - which may
        // be admin1, admin2, or even the group's creator (also force-promoted to admin). Whichever
        // of admin1/admin2 is NOT the anchor is exactly the case this feature adds: acting despite
        // not being current_owner_user_id.
        long anchorOwnerId = asAdmin1.get("currentOwnerUserId").asLong();
        String nonAnchorToken = anchorOwnerId == admin1.getId() ? admin2Token : admin1Token;

        mockMvc.perform(post("/api/documents/{id}/forward", documentId)
                        .header("Authorization", bearer(nonAnchorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "toUserId": %d, "forwardVisibility": "PUBLIC" }
                                """.formatted(finalRecipient.getId())))
                .andExpect(status().isOk());

        JsonNode finalState = getDocument(loginAndGetToken(finalRecipient.getUsername(), password), documentId);
        assertThat(finalState.get("currentOwnerUserId").asLong()).isEqualTo(finalRecipient.getId());
        assertThat(finalState.has("currentOwnerGroupId") && !finalState.get("currentOwnerGroupId").isNull())
                .as("forwarding onward to a person clears group-held state")
                .isFalse();
    }

    @Test
    void groupMemberIsCcOnlyAndNonMemberCannotViewOrAct() throws Exception {
        String password = "Grp1234";
        User dc = createUser("DC", "gf2-dc-", password);
        User groupOwnerAdmin = createUser("ADMIN", "gf2-owner-", password);
        User groupAdmin = createUser("DDC", "gf2-admin-", password);
        User groupMember = createUser("SC", "gf2-member-", password);
        User outsider = createUser("ASC", "gf2-outsider-", password);
        grantPermission(groupMember.getId(), "CC_VIEW_DOCUMENT");

        String dcToken = loginAndGetToken(dc.getUsername(), password);
        String memberToken = loginAndGetToken(groupMember.getUsername(), password);
        String outsiderToken = loginAndGetToken(outsider.getUsername(), password);

        long groupId = createGroup(loginAndGetToken(groupOwnerAdmin.getUsername(), password),
                "CC Only Group", groupAdmin.getId(), true, groupMember.getId(), false);
        long documentId = createDocument(dcToken, "gf2-cconly");
        // PRIVATE so the "non-member can't view" assertion below isn't confounded by the
        // pre-existing (group-unrelated) rule that any VIEW_PUBLIC_DOCUMENT holder can see PUBLIC docs.
        forwardToGroup(dcToken, documentId, groupId, "PRIVATE").andExpect(status().isOk());

        // A regular member cannot act...
        mockMvc.perform(post("/api/documents/{id}/forward", documentId)
                        .header("Authorization", bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "toUserId": %d, "forwardVisibility": "PUBLIC" }
                                """.formatted(dc.getId())))
                .andExpect(status().isBadRequest());

        // ...but can view, as CC.
        JsonNode asMember = getDocument(memberToken, documentId);
        assertThat(asMember.get("recipientType").asText()).isEqualTo("CC");
        assertThat(asMember.get("canWorkflow").asBoolean()).isFalse();

        // A non-member can neither view nor act.
        mockMvc.perform(get("/api/documents/{id}", documentId).header("Authorization", bearer(outsiderToken)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/documents/{id}/forward", documentId)
                        .header("Authorization", bearer(outsiderToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "toUserId": %d, "forwardVisibility": "PUBLIC" }
                                """.formatted(dc.getId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void forwardRequiresExactlyOneOfPersonOrGroupTarget() throws Exception {
        String password = "Grp1234";
        User dc = createUser("DC", "gf3-dc-", password);
        User groupOwnerAdmin = createUser("ADMIN", "gf3-owner-", password);
        User groupAdmin = createUser("DDC", "gf3-admin-", password);
        String dcToken = loginAndGetToken(dc.getUsername(), password);

        long groupId = createGroup(loginAndGetToken(groupOwnerAdmin.getUsername(), password),
                "Validation Group", groupAdmin.getId(), true);
        long documentId = createDocument(dcToken, "gf3-validation");

        // Neither target set.
        mockMvc.perform(post("/api/documents/{id}/forward", documentId)
                        .header("Authorization", bearer(dcToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "forwardVisibility": "PUBLIC" }
                                """))
                .andExpect(status().isBadRequest());

        // Both targets set.
        mockMvc.perform(post("/api/documents/{id}/forward", documentId)
                        .header("Authorization", bearer(dcToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "toUserId": %d, "toGroupId": %d, "forwardVisibility": "PUBLIC" }
                                """.formatted(groupAdmin.getId(), groupId)))
                .andExpect(status().isBadRequest());

        // Non-existent group.
        mockMvc.perform(post("/api/documents/{id}/forward", documentId)
                        .header("Authorization", bearer(dcToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "toGroupId": 9999999, "forwardVisibility": "PUBLIC" }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void movementHistoryAndSentMessagesShowTheTargetGroupNotJustItsAnchorAdmin() throws Exception {
        String password = "Grp1234";
        User dc = createUser("DC", "gf4-dc-", password);
        User groupOwnerAdmin = createUser("ADMIN", "gf4-owner-", password);
        User groupAdmin = createUser("DDC", "gf4-admin-", password);
        String dcToken = loginAndGetToken(dc.getUsername(), password);

        long groupId = createGroup(loginAndGetToken(groupOwnerAdmin.getUsername(), password),
                "Timeline Group", groupAdmin.getId(), true);
        long documentId = createDocument(dcToken, "gf4-timeline");
        forwardToGroup(dcToken, documentId, groupId).andExpect(status().isOk());

        MvcResult movementsResult = mockMvc.perform(get("/api/documents/{id}/movements", documentId)
                        .header("Authorization", bearer(dcToken)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode movements = readJson(movementsResult);
        JsonNode forwardMovement = null;
        for (JsonNode m : movements) {
            if ("FORWARD".equals(m.get("actionType").asText())) forwardMovement = m;
        }
        assertThat(forwardMovement).as("forward movement recorded").isNotNull();
        assertThat(forwardMovement.get("toGroupId").asLong()).isEqualTo(groupId);
        assertThat(forwardMovement.get("toGroupName").asText()).isEqualTo("Timeline Group");

        MvcResult sentResult = mockMvc.perform(get("/api/documents/sent-messages")
                        .header("Authorization", bearer(dcToken)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode sentContent = readJson(sentResult).get("content");
        JsonNode sentRow = null;
        for (JsonNode row : sentContent) {
            if (row.get("documentId").asLong() == documentId) sentRow = row;
        }
        assertThat(sentRow).as("sent-messages row for the group-forward").isNotNull();
        assertThat(sentRow.get("toGroupId").asLong()).isEqualTo(groupId);
        assertThat(sentRow.get("toGroupName").asText()).isEqualTo("Timeline Group");
    }

    @Test
    void everyGroupAdminGetsAnAssignedNotificationButPlainMembersOnlyGetCopied() throws Exception {
        String password = "Grp1234";
        User dc = createUser("DC", "gf5-dc-", password);
        User groupOwnerAdmin = createUser("ADMIN", "gf5-owner-", password);
        User admin1 = createUser("DDC", "gf5-admin1-", password);
        User plainMember = createUser("SC", "gf5-member-", password);
        String dcToken = loginAndGetToken(dc.getUsername(), password);

        long groupId = createGroup(loginAndGetToken(groupOwnerAdmin.getUsername(), password),
                "Notify Group", admin1.getId(), true, plainMember.getId(), false);
        long documentId = createDocument(dcToken, "gf5-notify");

        Mockito.clearInvocations(notificationWebSocketHandler);
        forwardToGroup(dcToken, documentId, groupId).andExpect(status().isOk());

        ArgumentCaptor<Long> userIdCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<RealtimeNotificationMessage> messageCaptor = ArgumentCaptor.forClass(RealtimeNotificationMessage.class);
        Mockito.verify(notificationWebSocketHandler, Mockito.atLeastOnce())
                .sendToUser(userIdCaptor.capture(), messageCaptor.capture());

        Map<Long, String> typeByRecipient = new HashMap<>();
        for (int i = 0; i < userIdCaptor.getAllValues().size(); i++) {
            typeByRecipient.put(userIdCaptor.getAllValues().get(i), messageCaptor.getAllValues().get(i).type());
        }

        // Both group admins - whichever one ended up as the anchor and whichever didn't - are
        // full co-owners of this document, so both must get an "assigned" style push, never the
        // passive "copied" one. The plain (non-admin) member genuinely can only view/copy, so it
        // must keep getting "copied" - proving the fix is scoped to admins, not the whole group.
        assertThat(typeByRecipient.get(groupOwnerAdmin.getId())).as("creator-admin notification type").isEqualTo("DOCUMENT_FORWARDED");
        assertThat(typeByRecipient.get(admin1.getId())).as("second admin notification type").isEqualTo("DOCUMENT_FORWARDED");
        assertThat(typeByRecipient.get(plainMember.getId())).as("plain member notification type").isEqualTo("DOCUMENT_COPIED");
    }

    // ---- helpers ----

    private org.springframework.test.web.servlet.ResultActions forwardToGroup(String token, long documentId, long groupId) throws Exception {
        return forwardToGroup(token, documentId, groupId, "PUBLIC");
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
                                  "title": "Group Forward Test Document",
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

    private void grantPermission(Long userId, String permissionName) {
        UserPermission up = new UserPermission();
        up.setUserId(userId);
        up.setPermissionName(permissionName);
        up.setEnabled(true);
        userPermissionRepository.saveAndFlush(up);
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
