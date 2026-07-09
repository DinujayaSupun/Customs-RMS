package lk.customs.rms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lk.customs.rms.entity.Document;
import lk.customs.rms.entity.Role;
import lk.customs.rms.entity.User;
import lk.customs.rms.entity.UserPermission;
import lk.customs.rms.enums.Priority;
import lk.customs.rms.enums.Status;
import lk.customs.rms.repository.DocumentRepository;
import lk.customs.rms.repository.RoleRepository;
import lk.customs.rms.repository.UserPermissionRepository;
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
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class RecipientGroupIntegrationTests {

    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private DocumentRepository documentRepository;
    @Autowired private UserPermissionRepository userPermissionRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
    }

    @Test
    void adminCreatesListsUpdatesAndDeletesGroup() throws Exception {
        String password = "Grp1234";
        User admin = createUser("ADMIN", "grp-admin-", password);
        User m1 = createUser("DC", "grp-m1-", password);
        User m2 = createUser("SC", "grp-m2-", password);
        String adminToken = loginAndGetToken(admin.getUsername(), password);

        MvcResult created = mockMvc.perform(post("/api/groups")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(groupPayload("Clearance Unit A", "#2563eb", m1.getId(), true, m2.getId(), false)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Clearance Unit A"))
                .andExpect(jsonPath("$.color").value("#2563eb"))
                .andReturn();
        long groupId = readJson(created).get("id").asLong();

        mockMvc.perform(get("/api/groups").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + groupId + ")].name").value(org.hamcrest.Matchers.hasItem("Clearance Unit A")));

        // Rename + change members.
        mockMvc.perform(put("/api/groups/{id}", groupId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(groupPayload("Clearance Unit A (renamed)", "#16a34a", m1.getId(), true, m2.getId(), true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Clearance Unit A (renamed)"))
                .andExpect(jsonPath("$.adminCount").value(2));

        mockMvc.perform(delete("/api/groups/{id}", groupId).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/groups").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + groupId + ")]").isEmpty());
    }

    @Test
    void creatingGroupRequiresManageGroupsPermissionAndCreatorBecomesAdmin() throws Exception {
        String password = "Grp1234";
        User plain = createUser("SC", "grp-plain-", password);      // SC role lacks MANAGE_GROUPS
        User member = createUser("DC", "grp-member-", password);
        String plainToken = loginAndGetToken(plain.getUsername(), password);

        // Without MANAGE_GROUPS -> rejected (400, the app's permission-denied status).
        mockMvc.perform(post("/api/groups")
                        .header("Authorization", bearer(plainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(groupPayload("Should Fail", "#111827", member.getId(), true)))
                .andExpect(status().isBadRequest());

        // Grant MANAGE_GROUPS to this user (per-user override from Feature 2).
        grantManageGroups(plain.getId());

        // Now it succeeds, and the creator is auto-added as an admin even though not in the member list.
        MvcResult created = mockMvc.perform(post("/api/groups")
                        .header("Authorization", bearer(plainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(groupPayload("Owned By Creator", "#111827", member.getId(), false)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode members = readJson(created).get("members");
        JsonNode creatorEntry = null;
        for (JsonNode e : members) {
            if (e.get("userId").asLong() == plain.getId()) { creatorEntry = e; break; }
        }
        assertThat(creatorEntry).as("creator is a member").isNotNull();
        assertThat(creatorEntry.get("isAdmin").asBoolean()).as("creator is an admin").isTrue();
    }

    @Test
    void groupAdminCanManageButPlainMemberCannotAndSystemAdminOverrides() throws Exception {
        String password = "Grp1234";
        User admin = createUser("ADMIN", "grp-sysadmin-", password);
        User groupAdmin = createUser("DC", "grp-gadmin-", password);
        User plainMember = createUser("SC", "grp-pmember-", password);
        String adminToken = loginAndGetToken(admin.getUsername(), password);
        String groupAdminToken = loginAndGetToken(groupAdmin.getUsername(), password);
        String plainMemberToken = loginAndGetToken(plainMember.getUsername(), password);

        long groupId = readJson(mockMvc.perform(post("/api/groups")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(groupPayload("Mgmt Group", "#0ea5e9", groupAdmin.getId(), true, plainMember.getId(), false)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asLong();

        // A group admin can edit it (even without MANAGE_GROUPS — DC role lacks it, but they're a group admin).
        mockMvc.perform(put("/api/groups/{id}", groupId)
                        .header("Authorization", bearer(groupAdminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(groupPayload("Mgmt Group v2", "#0ea5e9", groupAdmin.getId(), true, plainMember.getId(), false)))
                .andExpect(status().isOk());

        // A plain (non-admin) member cannot edit or delete.
        mockMvc.perform(put("/api/groups/{id}", groupId)
                        .header("Authorization", bearer(plainMemberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(groupPayload("Hacked", "#000000", groupAdmin.getId(), true)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(delete("/api/groups/{id}", groupId).header("Authorization", bearer(plainMemberToken)))
                .andExpect(status().isBadRequest());

        // System ADMIN can manage any group.
        mockMvc.perform(delete("/api/groups/{id}", groupId).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
    }

    @Test
    void anyAuthenticatedUserCanListGroups() throws Exception {
        String password = "Grp1234";
        User admin = createUser("ADMIN", "grp-listadmin-", password);
        User plain = createUser("PMA", "grp-listplain-", password);
        String adminToken = loginAndGetToken(admin.getUsername(), password);
        String plainToken = loginAndGetToken(plain.getUsername(), password);

        mockMvc.perform(post("/api/groups").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(groupPayload("Visible To All", "#f59e0b", plain.getId(), true)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/groups").header("Authorization", bearer(plainToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Visible To All')]").exists());
    }

    @Test
    void createGroupValidationRejectsBadInput() throws Exception {
        String password = "Grp1234";
        User admin = createUser("ADMIN", "grp-valadmin-", password);
        User member = createUser("DC", "grp-valmember-", password);
        String adminToken = loginAndGetToken(admin.getUsername(), password);

        // Blank name.
        mockMvc.perform(post("/api/groups").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(groupPayload("   ", "#111827", member.getId(), true)))
                .andExpect(status().isBadRequest());

        // Non-existent member.
        mockMvc.perform(post("/api/groups").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(groupPayload("Ghost Member", "#111827", 9_999_999L, true)))
                .andExpect(status().isBadRequest());

        // Inactive (deactivated) member.
        User inactiveMember = createUser("DC", "grp-inactive-", password);
        inactiveMember.setIsActive(false);
        userRepository.saveAndFlush(inactiveMember);
        mockMvc.perform(post("/api/groups").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(groupPayload("Inactive Member Group", "#111827", inactiveMember.getId(), true)))
                .andExpect(status().isBadRequest());

        // Duplicate name.
        MvcResult dupeCreated = mockMvc.perform(post("/api/groups").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(groupPayload("Dupe Group", "#111827", member.getId(), true)))
                .andExpect(status().isCreated())
                .andReturn();
        mockMvc.perform(post("/api/groups").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(groupPayload("Dupe Group", "#111827", member.getId(), true)))
                .andExpect(status().isBadRequest());

        // Zero admins: create auto-promotes the creator, so this can only be provoked on update,
        // where the member list is authoritative and demoting every admin must be rejected.
        long dupeGroupId = readJson(dupeCreated).get("id").asLong();
        mockMvc.perform(put("/api/groups/{id}", dupeGroupId).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(groupPayload("Dupe Group", "#111827", member.getId(), false)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cannotDeleteGroupWhileHoldingActiveDocuments() throws Exception {
        String password = "Grp1234";
        User admin = createUser("ADMIN", "grp-holdadmin-", password);
        User member = createUser("DC", "grp-holdmember-", password);
        String adminToken = loginAndGetToken(admin.getUsername(), password);

        long groupId = readJson(mockMvc.perform(post("/api/groups").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(groupPayload("Holding Group", "#111827", member.getId(), true)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asLong();

        // A document held by the group.
        saveDocumentHeldByGroup(admin.getId(), groupId, member.getId());

        mockMvc.perform(delete("/api/groups/{id}", groupId).header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anyUserCanListDocumentsHeldByAGroup() throws Exception {
        String password = "Grp1234";
        User admin = createUser("ADMIN", "grp-docsadmin-", password);
        User member = createUser("DC", "grp-docsmember-", password);
        User plainViewer = createUser("PMA", "grp-docsviewer-", password);
        String adminToken = loginAndGetToken(admin.getUsername(), password);
        String viewerToken = loginAndGetToken(plainViewer.getUsername(), password);

        long groupId = readJson(mockMvc.perform(post("/api/groups").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(groupPayload("Docs Group", "#111827", member.getId(), true)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asLong();

        // Empty at first.
        mockMvc.perform(get("/api/groups/{id}/documents", groupId).header("Authorization", bearer(viewerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));

        saveDocumentHeldByGroup(admin.getId(), groupId, member.getId());

        mockMvc.perform(get("/api/groups/{id}/documents", groupId).header("Authorization", bearer(viewerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].refNo").value(org.hamcrest.Matchers.startsWith("GRP-HELD-")));
    }

    // ---- helpers ----

    private String groupPayload(String name, String color, Object... memberIdThenIsAdmin) {
        StringBuilder members = new StringBuilder();
        for (int i = 0; i < memberIdThenIsAdmin.length; i += 2) {
            if (i > 0) members.append(",");
            members.append("{\"userId\":").append(memberIdThenIsAdmin[i])
                    .append(",\"isAdmin\":").append(memberIdThenIsAdmin[i + 1]).append("}");
        }
        return """
                { "name": "%s", "color": "%s", "members": [ %s ] }
                """.formatted(name, color, members);
    }

    private void grantManageGroups(Long userId) {
        UserPermission up = new UserPermission();
        up.setUserId(userId);
        up.setPermissionName("MANAGE_GROUPS");
        up.setEnabled(true);
        userPermissionRepository.saveAndFlush(up);
    }

    private void saveDocumentHeldByGroup(Long creatorUserId, Long groupId, Long primaryAdminUserId) {
        Document d = new Document();
        d.setRefNo("GRP-HELD-" + UUID.randomUUID());
        d.setTitle("Held by group");
        d.setReceivedDate(LocalDate.now());
        d.setCompanyName("Integration Co");
        d.setVisibility("PUBLIC");
        d.setPriority(Priority.HIGH);
        d.setStatus(Status.IN_PROGRESS);
        d.setCreatedByUserId(creatorUserId);
        d.setCurrentOwnerUserId(primaryAdminUserId);
        d.setCurrentOwnerGroupId(groupId);
        d.setCreatedAt(LocalDateTime.now());
        d.setUpdatedAt(LocalDateTime.now());
        d.setDeleted(false);
        documentRepository.saveAndFlush(d);
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
