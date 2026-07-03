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
class AdminAndWorkflowRoutingIntegrationTests {

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
    void senderCanUndoLatestUnopenedForwardWithinConfiguredWindow() throws Exception {
        String password = "Undo1234";
        User sender = createUser("DC", "undo-sender-", password);
        User receiver = createUser("DDC", "undo-receiver-", password);
        String senderToken = loginAndGetToken(sender.getUsername(), password);

        long documentId = createDocument(sender, senderToken, "undo-forward");
        forwardDocument(documentId, senderToken, receiver.getId());

        mockMvc.perform(get("/api/documents/sent-messages")
                        .header("Authorization", bearer(senderToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].documentId").value(documentId))
                .andExpect(jsonPath("$.content[0].canUndoSend").value(true))
                .andExpect(jsonPath("$.content[0].undoSendStatus").value("AVAILABLE"));

        mockMvc.perform(post("/api/documents/{id}/undo-send", documentId)
                        .header("Authorization", bearer(senderToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Sent to wrong officer\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/documents/{id}", documentId)
                        .header("Authorization", bearer(senderToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentOwnerUserId").value(sender.getId()));

        mockMvc.perform(get("/api/documents")
                        .header("Authorization", bearer(senderToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].documentId").doesNotExist())
                .andExpect(jsonPath("$.content[0].id").value(documentId))
                .andExpect(jsonPath("$.content[0].undoSendActionType").value("UNDO_SEND"))
                .andExpect(jsonPath("$.content[0].undoSendByUserId").value(sender.getId()))
                .andExpect(jsonPath("$.content[0].undoSendByName").value(sender.getFullName()))
                .andExpect(jsonPath("$.content[0].undoSendByRole").value("DC"))
                .andExpect(jsonPath("$.content[0].undoSendFromUserId").value(receiver.getId()))
                .andExpect(jsonPath("$.content[0].undoSendFromName").value(receiver.getFullName()))
                .andExpect(jsonPath("$.content[0].undoSendFromRole").value("DDC"));

        mockMvc.perform(get("/api/documents/sent-messages")
                        .header("Authorization", bearer(senderToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].documentId").value(documentId))
                .andExpect(jsonPath("$.content[0].canUndoSend").value(false))
                .andExpect(jsonPath("$.content[0].undoSendByUserId").value(sender.getId()))
                .andExpect(jsonPath("$.content[0].undoSendByName").value(sender.getFullName()))
                .andExpect(jsonPath("$.content[0].undoSendByRole").value("DC"));

        String receiverToken = loginAndGetToken(receiver.getUsername(), password);
        mockMvc.perform(get("/api/documents/sent-messages")
                        .header("Authorization", bearer(receiverToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].documentId").value(documentId))
                .andExpect(jsonPath("$.content[0].undoSendActionType").value("UNDO_SEND"))
                .andExpect(jsonPath("$.content[0].undoSendByUserId").value(sender.getId()))
                .andExpect(jsonPath("$.content[0].undoSendByName").value(sender.getFullName()))
                .andExpect(jsonPath("$.content[0].undoSendByRole").value("DC"));
    }

    @Test
    void senderCannotUndoForwardAfterReceiverOpenedDocument() throws Exception {
        String password = "Undo1234";
        User sender = createUser("DC", "undo-open-sender-", password);
        User receiver = createUser("DDC", "undo-open-receiver-", password);
        String senderToken = loginAndGetToken(sender.getUsername(), password);
        String receiverToken = loginAndGetToken(receiver.getUsername(), password);

        long documentId = createDocument(sender, senderToken, "undo-opened");
        forwardDocument(documentId, senderToken, receiver.getId());

        mockMvc.perform(get("/api/documents/{id}", documentId)
                        .header("Authorization", bearer(receiverToken)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/documents/{id}/undo-send", documentId)
                        .header("Authorization", bearer(senderToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Need to revise\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/documents/sent-messages")
                        .header("Authorization", bearer(senderToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].documentId").value(documentId))
                .andExpect(jsonPath("$.content[0].canUndoSend").value(false))
                .andExpect(jsonPath("$.content[0].undoSendStatus").value("OPENED"));
    }

    @Test
    void receivedInboxSeparatesActualSenderFromLatestMinuteAuthor() throws Exception {
        String password = "Flow1234";
        User dc = createUser("DC", "inbox-sender-dc-", password);
        User ddc = createUser("DDC", "inbox-minute-ddc-", password);

        String dcToken = loginAndGetToken(dc.getUsername(), password);
        String ddcToken = loginAndGetToken(ddc.getUsername(), password);

        long documentId = createDocument(dc, dcToken, "inbox-sender-minute");

        mockMvc.perform(post("/api/documents/{id}/forward", documentId)
                        .header("Authorization", bearer(dcToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "toUserId": %d,
                                  "forwardVisibility": "PRIVATE",
                                  "remarkText": "Sent by DC"
                                }
                                """.formatted(ddc.getId())))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/documents/{id}/remarks", documentId)
                        .header("Authorization", bearer(ddcToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remarkText\":\"Latest minute by DDC\"}"))
                .andExpect(status().isCreated());

        MvcResult inboxResult = mockMvc.perform(get("/api/documents")
                        .header("Authorization", bearer(ddcToken))
                        .param("page", "0")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode inboxDoc = findDocumentInPage(readJson(inboxResult), documentId);
        assertThat(inboxDoc.get("inboxSenderUserId").asLong()).isEqualTo(dc.getId());
        assertThat(inboxDoc.get("inboxSenderName").asText()).isEqualTo(dc.getFullName());
        assertThat(inboxDoc.get("inboxSenderRole").asText()).isEqualTo("DC");
        assertThat(inboxDoc.get("latestRemarkByUserId").asLong()).isEqualTo(ddc.getId());
        assertThat(inboxDoc.get("latestRemarkByName").asText()).isEqualTo(ddc.getFullName());
        assertThat(inboxDoc.get("latestRemarkByRole").asText()).isEqualTo("DDC");
        assertThat(inboxDoc.get("latestRemarkTextPreview").asText()).isEqualTo("Latest minute by DDC");
        assertThat(inboxDoc.get("latestRemarkText").asText()).isEqualTo("Latest minute by DDC");
    }

    @Test
    void currentOwnerCanEditAndDeleteOwnLatestMinuteInCurrentOwnershipPeriod() throws Exception {
        String password = "Flow1234";
        User dc = createUser("DC", "minute-edit-dc-", password);
        String dcToken = loginAndGetToken(dc.getUsername(), password);
        long documentId = createDocument(dc, dcToken, "minute-edit");

        long remarkId = addMinute(documentId, dcToken, "Original latest minute");

        mockMvc.perform(get("/api/documents/{documentId}/remarks", documentId)
                        .header("Authorization", bearer(dcToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].canEdit").value(true))
                .andExpect(jsonPath("$[0].canDelete").value(true));

        mockMvc.perform(put("/api/documents/{documentId}/remarks/{remarkId}", documentId, remarkId)
                        .header("Authorization", bearer(dcToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remarkText\":\"Updated latest minute\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remarkText").value("Updated latest minute"))
                .andExpect(jsonPath("$.canEdit").value(true))
                .andExpect(jsonPath("$.canDelete").value(true));

        mockMvc.perform(delete("/api/documents/{documentId}/remarks/{remarkId}", documentId, remarkId)
                        .header("Authorization", bearer(dcToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/documents/{documentId}/remarks", documentId)
                        .header("Authorization", bearer(dcToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void olderMinuteFromPastOwnershipPeriodCannotBeEditedOrDeleted() throws Exception {
        String password = "Flow1234";
        User dc = createUser("DC", "minute-old-dc-", password);
        User ddc = createUser("DDC", "minute-old-ddc-", password);
        String dcToken = loginAndGetToken(dc.getUsername(), password);
        String ddcToken = loginAndGetToken(ddc.getUsername(), password);
        long documentId = createDocument(dc, dcToken, "minute-old");

        mockMvc.perform(post("/api/documents/{id}/forward", documentId)
                        .header("Authorization", bearer(dcToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "toUserId": %d,
                                  "forwardVisibility": "PRIVATE"
                                }
                                """.formatted(ddc.getId())))
                .andExpect(status().isOk());

        long oldRemarkId = addMinute(documentId, ddcToken, "Old DDC minute");

        mockMvc.perform(post("/api/documents/{id}/return", documentId)
                        .header("Authorization", bearer(ddcToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "toUserId": %d
                                }
                                """.formatted(dc.getId())))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/documents/{id}/forward", documentId)
                        .header("Authorization", bearer(dcToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "toUserId": %d,
                                  "forwardVisibility": "PRIVATE"
                                }
                                """.formatted(ddc.getId())))
                .andExpect(status().isOk());

        addMinute(documentId, ddcToken, "New DDC minute");

        mockMvc.perform(get("/api/documents/{documentId}/remarks", documentId)
                        .header("Authorization", bearer(ddcToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].canEdit").value(false))
                .andExpect(jsonPath("$[0].canDelete").value(false))
                .andExpect(jsonPath("$[1].canEdit").value(true))
                .andExpect(jsonPath("$[1].canDelete").value(true));

        mockMvc.perform(put("/api/documents/{documentId}/remarks/{remarkId}", documentId, oldRemarkId)
                        .header("Authorization", bearer(ddcToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remarkText\":\"Trying to edit old minute\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only your latest minute in the current ownership period can be changed."));

        mockMvc.perform(delete("/api/documents/{documentId}/remarks/{remarkId}", documentId, oldRemarkId)
                        .header("Authorization", bearer(ddcToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only your latest minute in the current ownership period can be changed."));
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

    @Test
    void returnAndIssueActionsRefreshUpdatedAt() throws Exception {
        String password = "Flow1234";
        User dc = createUser("DC", "updated-dc-", password);
        User ddc = createUser("DDC", "updated-ddc-", password);

        String dcToken = loginAndGetToken(dc.getUsername(), password);
        String ddcToken = loginAndGetToken(ddc.getUsername(), password);

        long documentId = createDocument(dc, dcToken, "updated-at");
        LocalDateTime createdUpdatedAt = fetchUpdatedAt(documentId, dcToken);

        mockMvc.perform(post("/api/documents/{id}/forward", documentId)
                        .header("Authorization", bearer(dcToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "toUserId": %d,
                                  "forwardVisibility": "PUBLIC"
                                }
                                """.formatted(ddc.getId())))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/documents/{id}/return", documentId)
                        .header("Authorization", bearer(ddcToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "toUserId": %d
                                }
                                """.formatted(dc.getId())))
                .andExpect(status().isOk());

        LocalDateTime returnedUpdatedAt = fetchUpdatedAt(documentId, dcToken);
        assertThat(returnedUpdatedAt).isAfterOrEqualTo(createdUpdatedAt);

        mockMvc.perform(post("/api/documents/{id}/approve", documentId)
                        .header("Authorization", bearer(dcToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remarkText\":\"Approved before done\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/documents/{id}/issue", documentId)
                        .header("Authorization", bearer(dcToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remarkText\":\"Done in test\"}"))
                .andExpect(status().isOk());

        LocalDateTime issuedUpdatedAt = fetchUpdatedAt(documentId, dcToken);
        assertThat(issuedUpdatedAt).isAfterOrEqualTo(returnedUpdatedAt);
    }

    @Test
    void issuedDocumentCannotBeReturnedEvenByCurrentOwner() throws Exception {
        String password = "Flow1234";
        User admin = createUser("ADMIN", "issued-admin-", password);
        User ddc = createUser("DDC", "issued-ddc-", password);
        String adminToken = loginAndGetToken(admin.getUsername(), password);

        long documentId = createDocument(admin, adminToken, "issued-return");

        mockMvc.perform(post("/api/documents/{id}/approve", documentId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remarkText\":\"Approved before done\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/documents/{id}/issue", documentId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remarkText\":\"Done before return attempt\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/documents/{id}/return", documentId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "toUserId": %d
                                }
                                """.formatted(ddc.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cannot forward/return a ISSUED document. Allowed statuses: PENDING, IN_PROGRESS, RETURNED."));
    }

    @Test
    void pendingDocumentCannotBeIssuedBeforeApproval() throws Exception {
        String password = "Flow1234";
        User admin = createUser("ADMIN", "issue-pending-admin-", password);
        String adminToken = loginAndGetToken(admin.getUsername(), password);

        long documentId = createDocument(admin, adminToken, "issue-pending");

        mockMvc.perform(post("/api/documents/{id}/issue", documentId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remarkText\":\"Trying to issue too early\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cannot issue. Document must be APPROVED first."));
    }

    @Test
    void pendingDocumentCanBeIssuedWhenApproveRejectButtonsAreDisabled() throws Exception {
        disableApproveRejectButtons();

        String password = "Flow1234";
        User admin = createUser("ADMIN", "issue-direct-admin-", password);
        String adminToken = loginAndGetToken(admin.getUsername(), password);

        long documentId = createDocument(admin, adminToken, "issue-direct");

        mockMvc.perform(post("/api/documents/{id}/issue", documentId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remarkText\":\"Direct done when approval step is disabled\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/documents/{id}", documentId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ISSUED"));
    }

    @Test
    void approvedDocumentCannotBeApprovedAgain() throws Exception {
        String password = "Flow1234";
        User admin = createUser("ADMIN", "approve-again-admin-", password);
        String adminToken = loginAndGetToken(admin.getUsername(), password);

        long documentId = createDocument(admin, adminToken, "approve-again");

        mockMvc.perform(post("/api/documents/{id}/approve", documentId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remarkText\":\"First approval\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/documents/{id}/approve", documentId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remarkText\":\"Second approval should fail\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Document is already APPROVED. Only ISSUE is allowed."));
    }

    @Test
    void reopenRequiresReason() throws Exception {
        String password = "Flow1234";
        User admin = createUser("ADMIN", "reopen-reason-admin-", password);
        String adminToken = loginAndGetToken(admin.getUsername(), password);

        long documentId = createDocument(admin, adminToken, "reopen-reason");

        mockMvc.perform(post("/api/documents/{id}/approve", documentId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remarkText\":\"Approved before reopen\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/documents/{id}/reopen", documentId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remarkText\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Reopen requires a reason (remarkText must not be empty)."));
    }

    @Test
    void documentAuditHistoryReturnsForwardEventAfterForwarding() throws Exception {
        String password = "AuditHist123";
        User admin = createUser("ADMIN", "audit-hist-admin-", password);
        User ddc = createUser("DDC", "audit-hist-ddc-", password);
        String adminToken = loginAndGetToken(admin.getUsername(), password);

        long documentId = createDocument(admin, adminToken, "audit-hist");
        forwardDocument(documentId, adminToken, ddc.getId());

        MvcResult result = mockMvc.perform(get("/api/documents/{id}/audit-logs", documentId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode logs = readJson(result);
        assertThat(logs.isArray()).isTrue();
        assertThat(logs.size()).isGreaterThan(0);

        boolean hasForwardEntry = false;
        for (JsonNode log : logs) {
            if ("FORWARD".equals(log.path("actionType").asText())) {
                hasForwardEntry = true;
                break;
            }
        }
        assertThat(hasForwardEntry).as("audit log should contain a FORWARD entry for document %d", documentId).isTrue();
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

    private void forwardDocument(long documentId, String token, long toUserId) throws Exception {
        mockMvc.perform(post("/api/documents/{id}/forward", documentId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "toUserId": %d,
                                  "forwardVisibility": "PUBLIC",
                                  "remarkText": "Please check this"
                                }
                                """.formatted(toUserId)))
                .andExpect(status().isOk());
    }

    private long addMinute(long documentId, String token, String text) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/documents/{documentId}/remarks", documentId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remarkText\":\"%s\"}".formatted(text)))
                .andExpect(status().isCreated())
                .andReturn();

        return readJson(result).get("id").asLong();
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

    private LocalDateTime fetchUpdatedAt(long documentId, String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/documents/{id}", documentId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = readJson(result);
        assertThat(json.get("updatedAt").isNull()).isFalse();
        return LocalDateTime.parse(json.get("updatedAt").asText());
    }

    private JsonNode findDocumentInPage(JsonNode page, long documentId) {
        for (JsonNode item : page.get("content")) {
            if (item.get("id").asLong() == documentId) {
                return item;
            }
        }
        throw new AssertionError("Document not found in page: " + documentId);
    }

    private void ensureApproveRejectButtonsEnabled() {
        DcAutoForwardConfig config = dcAutoForwardConfigRepository.findById(1L).orElseGet(DcAutoForwardConfig::new);
        config.setId(1L);
        config.setApproveRejectButtonsEnabled(true);
        config.setForwardReturnAllowedStatuses("PENDING,IN_PROGRESS,RETURNED");
        config.setUndoSendEnabled(true);
        config.setUndoSendWindowHours(24);
        config.setUndoSendRequiresUnopened(true);
        config.setUndoSendAllowedActions("FORWARD,RETURN");
        config.setUndoSendRequiresReason(true);
        config.setUndoSendNotifyReceiver(true);
        config.setUndoSendShowExpiredInfo(true);
        dcAutoForwardConfigRepository.saveAndFlush(config);
    }

    private void disableApproveRejectButtons() {
        DcAutoForwardConfig config = dcAutoForwardConfigRepository.findById(1L).orElseGet(DcAutoForwardConfig::new);
        config.setId(1L);
        config.setApproveRejectButtonsEnabled(false);
        config.setForwardReturnAllowedStatuses("PENDING,IN_PROGRESS,RETURNED");
        config.setUndoSendEnabled(true);
        config.setUndoSendWindowHours(24);
        config.setUndoSendRequiresUnopened(true);
        config.setUndoSendAllowedActions("FORWARD,RETURN");
        config.setUndoSendRequiresReason(true);
        config.setUndoSendNotifyReceiver(true);
        config.setUndoSendShowExpiredInfo(true);
        dcAutoForwardConfigRepository.saveAndFlush(config);
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
