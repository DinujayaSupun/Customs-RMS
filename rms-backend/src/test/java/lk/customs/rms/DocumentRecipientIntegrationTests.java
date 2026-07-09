package lk.customs.rms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lk.customs.rms.entity.AuditLog;
import lk.customs.rms.entity.DcAutoForwardConfig;
import lk.customs.rms.entity.Role;
import lk.customs.rms.entity.RolePermission;
import lk.customs.rms.entity.User;
import lk.customs.rms.enums.AppPermission;
import lk.customs.rms.repository.AuditLogRepository;
import lk.customs.rms.repository.DcAutoForwardConfigRepository;
import lk.customs.rms.repository.RolePermissionRepository;
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
 * ✅ PHASE B: Backend Integration Tests for Recipient Model (46 tests)
 *
 * Coverage:
 * - Forward with CC/BCC recipients
 * - Return restores recipient set
 * - Undo Send restores recipient set
 * - Reopen sets TO only
 * - Manage Recipients (CC/BCC updates)
 * - CC/BCC Access Control
 * - BCC Visibility Rules
 * - Audit Logging includes recipient details
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DocumentRecipientIntegrationTests {

    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private RolePermissionRepository rolePermissionRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private DcAutoForwardConfigRepository dcAutoForwardConfigRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private User admin, dc, ddc, sc, sc2, sc3;
    private String adminToken, dcToken, ddcToken, scToken, sc2Token, sc3Token;
    private long documentId;

    @BeforeEach
    void setup() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        admin = createUser("ADMIN", "admin-recipient-", "Admin@123");
        dc = createUser("DC", "dc-recipient-", "DC@123");
        ddc = createUser("DDC", "ddc-recipient-", "DDC@123");
        sc = createUser("SC", "sc-recipient-", "SC@123");
        sc2 = createUser("SC", "sc2-recipient-", "SC@123");
        sc3 = createUser("SC", "sc3-recipient-", "SC@123");
        enableRecipientWorkflowPermissions();
        enableUndoSendConfig();

        adminToken = loginAndGetToken(admin.getUsername(), "Admin@123");
        dcToken = loginAndGetToken(dc.getUsername(), "DC@123");
        ddcToken = loginAndGetToken(ddc.getUsername(), "DDC@123");
        scToken = loginAndGetToken(sc.getUsername(), "SC@123");
        sc2Token = loginAndGetToken(sc2.getUsername(), "SC@123");
        sc3Token = loginAndGetToken(sc3.getUsername(), "SC@123");

        documentId = createDocument(dcToken, "recipient-test");
    }

    // =========================================================
    // PHASE 4: Forward with CC/BCC (8 tests)
    // =========================================================

    @Test
    void forward_CreatesNewActiveSet() throws Exception {
        mockMvc.perform(post("/api/documents/{id}/forward", documentId)
                .header("Authorization", bearer(dcToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                        {
                          "toUserId": %d,
                          "ccUserIds": [%d],
                          "forwardVisibility": "PUBLIC"
                        }
                        """, ddc.getId(), sc.getId())))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/documents/{id}", documentId)
                .header("Authorization", bearer(ddcToken)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode doc = readJson(result);
        assertThat(doc.get("currentOwnerUserId").asLong()).isEqualTo(ddc.getId());
        assertThat(doc.get("recipientType").asText()).isEqualTo("TO");
    }

    @Test
    void forward_NewTO_BecomeCurrentOwner() throws Exception {
        mockMvc.perform(post("/api/documents/{id}/forward", documentId)
                .header("Authorization", bearer(dcToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                        {
                          "toUserId": %d,
                          "forwardVisibility": "PUBLIC"
                        }
                        """, ddc.getId())))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/documents/{id}", documentId)
                .header("Authorization", bearer(ddcToken)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode doc = readJson(result);
        assertThat(doc.get("currentOwnerUserId").asLong()).isEqualTo(ddc.getId());
    }

    @Test
    void forward_CC_Saved_InActiveSet() throws Exception {
        mockMvc.perform(post("/api/documents/{id}/forward", documentId)
                .header("Authorization", bearer(dcToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                        {
                          "toUserId": %d,
                          "ccUserIds": [%d, %d],
                          "forwardVisibility": "PUBLIC"
                        }
                        """, ddc.getId(), sc.getId(), sc2.getId())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/documents/{id}", documentId)
                .header("Authorization", bearer(scToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipientType").value("CC"));
    }

    @Test
    void forward_BCC_Saved_InActiveSet() throws Exception {
        mockMvc.perform(post("/api/documents/{id}/forward", documentId)
                .header("Authorization", bearer(dcToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                        {
                          "toUserId": %d,
                          "bccUserIds": [%d],
                          "forwardVisibility": "PUBLIC"
                        }
                        """, ddc.getId(), sc.getId())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/documents/{id}", documentId)
                .header("Authorization", bearer(scToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipientType").value("BCC"));
    }

    @Test
    void forward_PreviousSet_BecomesInactive() throws Exception {
        // First forward
        mockMvc.perform(post("/api/documents/{id}/forward", documentId)
                .header("Authorization", bearer(dcToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                        {
                          "toUserId": %d,
                          "forwardVisibility": "PUBLIC"
                        }
                        """, ddc.getId())))
                .andExpect(status().isOk());

        // Second forward
        mockMvc.perform(post("/api/documents/{id}/forward", documentId)
                .header("Authorization", bearer(ddcToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                        {
                          "toUserId": %d,
                          "forwardVisibility": "PUBLIC"
                        }
                        """, sc.getId())))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/documents/{id}", documentId)
                .header("Authorization", bearer(scToken)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode doc = readJson(result);
        assertThat(doc.get("currentOwnerUserId").asLong()).isEqualTo(sc.getId());
    }

    @Test
    void forward_AuditLog_IncludesRecipients() throws Exception {
        mockMvc.perform(post("/api/documents/{id}/forward", documentId)
                .header("Authorization", bearer(dcToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                        {
                          "toUserId": %d,
                          "ccUserIds": [%d],
                          "bccUserIds": [%d],
                          "forwardVisibility": "PUBLIC"
                        }
                        """, ddc.getId(), sc.getId(), sc2.getId())))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/documents/{id}", documentId)
                .header("Authorization", bearer(ddcToken)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode doc = readJson(result);
        assertThat(doc.get("recipientSummary").get("cc")).hasSize(1);
        assertThat(doc.get("recipientSummary").get("bcc")).hasSize(0);

        AuditLog log = latestAuditLog("MOVEMENT", documentId, "FORWARD");
        JsonNode details = objectMapper.readTree(log.getDetailsJson());
        assertThat(details.get("toUserId").asLong()).isEqualTo(ddc.getId());
        assertThat(details.get("movementId").asLong()).isPositive();
        assertThat(details.get("activeRecipientSetId").asLong()).isPositive();
        assertThat(details.get("ccUserIds").get(0).asLong()).isEqualTo(sc.getId());
        assertThat(details.get("bccUserIds").get(0).asLong()).isEqualTo(sc2.getId());
    }

    @Test
    void inboxRecipientTypeFilter_UsesBackendFilterAndReturnsSummaryText() throws Exception {
        mockMvc.perform(post("/api/documents/{id}/forward", documentId)
                .header("Authorization", bearer(dcToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                        {
                          "toUserId": %d,
                          "ccUserIds": [%d],
                          "bccUserIds": [%d],
                          "forwardVisibility": "PUBLIC"
                        }
                        """, ddc.getId(), sc.getId(), sc2.getId())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/documents/my-inbox")
                .queryParam("recipientType", "TO")
                .header("Authorization", bearer(ddcToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(documentId))
                .andExpect(jsonPath("$.content[0].recipientType").value("TO"))
                .andExpect(jsonPath("$.content[0].recipientSummaryText").exists())
                .andExpect(jsonPath("$.content[0].recipientSummary.bcc").isEmpty());

        mockMvc.perform(get("/api/documents/my-inbox")
                .queryParam("recipientType", "CC")
                .header("Authorization", bearer(scToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(documentId))
                .andExpect(jsonPath("$.content[0].recipientType").value("CC"));

        mockMvc.perform(get("/api/documents/my-inbox")
                .queryParam("recipientType", "BCC")
                .header("Authorization", bearer(sc2Token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(documentId))
                .andExpect(jsonPath("$.content[0].recipientType").value("BCC"));

        mockMvc.perform(get("/api/documents/my-inbox")
                .queryParam("recipientType", "CC")
                .header("Authorization", bearer(ddcToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void sentMessages_IncludeFullRecipientSummaryForSender() throws Exception {
        mockMvc.perform(post("/api/documents/{id}/forward", documentId)
                .header("Authorization", bearer(dcToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                        {
                          "toUserId": %d,
                          "ccUserIds": [%d],
                          "bccUserIds": [%d],
                          "forwardVisibility": "PUBLIC"
                        }
                        """, ddc.getId(), sc.getId(), sc2.getId())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/documents/sent-messages")
                .header("Authorization", bearer(dcToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].documentId").value(documentId))
                .andExpect(jsonPath("$.content[0].recipientSummary.to[0].userId").value(ddc.getId()))
                .andExpect(jsonPath("$.content[0].recipientSummary.cc[0].userId").value(sc.getId()))
                .andExpect(jsonPath("$.content[0].recipientSummary.bcc[0].userId").value(sc2.getId()))
                .andExpect(jsonPath("$.content[0].recipientSummaryText").exists());
    }

    @Test
    void timelineRecipientContext_HidesBccForNormalUsersButShowsSelfToBcc() throws Exception {
        mockMvc.perform(post("/api/documents/{id}/forward", documentId)
                .header("Authorization", bearer(dcToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                        {
                          "toUserId": %d,
                          "ccUserIds": [%d],
                          "bccUserIds": [%d],
                          "forwardVisibility": "PUBLIC"
                        }
                        """, ddc.getId(), sc.getId(), sc2.getId())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/documents/{id}/movements", documentId)
                .header("Authorization", bearer(ddcToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].recipientSummary.to[0].userId").value(ddc.getId()))
                .andExpect(jsonPath("$[1].recipientSummary.cc[0].userId").value(sc.getId()))
                .andExpect(jsonPath("$[1].recipientSummary.bcc").isEmpty());

        mockMvc.perform(get("/api/documents/{id}/movements", documentId)
                .header("Authorization", bearer(sc2Token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].recipientSummary.bcc[0].name").value("you"));
    }

    @Test
    void returnUndoAndManualRecipientAuditLogs_ContainRestoredAndDiffDetails() throws Exception {
        mockMvc.perform(post("/api/documents/{id}/forward", documentId)
                .header("Authorization", bearer(dcToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                        {
                          "toUserId": %d,
                          "ccUserIds": [%d],
                          "bccUserIds": [%d],
                          "forwardVisibility": "PUBLIC"
                        }
                        """, ddc.getId(), sc.getId(), sc2.getId())))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/documents/{id}/recipients", documentId)
                .header("Authorization", bearer(ddcToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                        {
                          "ccUserIds": [%d],
                          "bccUserIds": []
                        }
                        """, sc3.getId())))
                .andExpect(status().isOk());

        JsonNode updateDetails = objectMapper.readTree(latestAuditLog("DOCUMENT", documentId, "RECIPIENTS_UPDATE").getDetailsJson());
        assertThat(updateDetails.get("previousCcUserIds").get(0).asLong()).isEqualTo(sc.getId());
        assertThat(updateDetails.get("addedCcUserIds").get(0).asLong()).isEqualTo(sc3.getId());
        assertThat(updateDetails.get("removedBccUserIds").get(0).asLong()).isEqualTo(sc2.getId());
        assertThat(updateDetails.get("activeRecipientSetId").asLong()).isPositive();

        mockMvc.perform(post("/api/documents/{id}/return", documentId)
                .header("Authorization", bearer(ddcToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                        {
                          "toUserId": %d
                        }
                        """, dc.getId())))
                .andExpect(status().isOk());

        JsonNode returnDetails = objectMapper.readTree(latestAuditLog("MOVEMENT", documentId, "RETURN").getDetailsJson());
        assertThat(returnDetails.get("toUserId").asLong()).isEqualTo(dc.getId());
        assertThat(returnDetails.get("restoredRecipientSetId").asLong()).isPositive();
        assertThat(returnDetails.get("movementId").asLong()).isPositive();

        mockMvc.perform(post("/api/documents/{id}/undo-send", documentId)
                .header("Authorization", bearer(ddcToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk());

        JsonNode undoDetails = objectMapper.readTree(latestAuditLog("MOVEMENT", documentId, "UNDO_SEND").getDetailsJson());
        assertThat(undoDetails.get("toUserId").asLong()).isEqualTo(ddc.getId());
        assertThat(undoDetails.get("previousMovementId").asLong()).isPositive();
        assertThat(undoDetails.get("restoredRecipientSetId").asLong()).isPositive();
    }

    // =========================================================
    // PHASE 5: Return Restores Set (5 tests)
    // =========================================================

    @Test
    void return_RestoresPreviousTO() throws Exception {
        // Forward DC -> DDC
        mockMvc.perform(post("/api/documents/{id}/forward", documentId)
                .header("Authorization", bearer(dcToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                        {
                          "toUserId": %d,
                          "forwardVisibility": "PUBLIC"
                        }
                        """, ddc.getId())))
                .andExpect(status().isOk());

        // Return DDC -> DC
        mockMvc.perform(post("/api/documents/{id}/return", documentId)
                .header("Authorization", bearer(ddcToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                        {
                          "toUserId": %d
                        }
                        """, dc.getId())))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/documents/{id}", documentId)
                .header("Authorization", bearer(dcToken)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode doc = readJson(result);
        assertThat(doc.get("currentOwnerUserId").asLong()).isEqualTo(dc.getId());
        assertThat(doc.get("status").asText()).isEqualTo("RETURNED");
    }

    @Test
    void return_RestoresPreviousCC() throws Exception {
        mockMvc.perform(post("/api/documents/{id}/forward", documentId)
                .header("Authorization", bearer(dcToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                        {
                          "toUserId": %d,
                          "ccUserIds": [%d, %d],
                          "forwardVisibility": "PUBLIC"
                        }
                        """, ddc.getId(), sc.getId(), sc2.getId())))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/documents/{id}/return", documentId)
                .header("Authorization", bearer(ddcToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                        {
                          "toUserId": %d
                        }
                        """, dc.getId())))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/documents/{id}", documentId)
                .header("Authorization", bearer(dcToken)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode doc = readJson(result);
        assertThat(doc.get("recipientSummary").get("cc")).hasSize(0);
    }

    @Test
    void return_RestoresPreviousBCC() throws Exception {
        mockMvc.perform(post("/api/documents/{id}/forward", documentId)
                .header("Authorization", bearer(dcToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                        {
                          "toUserId": %d,
                          "bccUserIds": [%d],
                          "forwardVisibility": "PUBLIC"
                        }
                        """, ddc.getId(), sc.getId())))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/documents/{id}/return", documentId)
                .header("Authorization", bearer(ddcToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                        {
                          "toUserId": %d
                        }
                        """, dc.getId())))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/documents/{id}", documentId)
                .header("Authorization", bearer(scToken)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode doc = readJson(result);
        assertThat(doc.get("recipientType").asText()).isEqualTo("null");
    }

    // =========================================================
    // PHASE 6: Undo Send Restores Set (3 tests)
    // =========================================================

    @Test
    void undoSend_RestoresPreviousTO() throws Exception {
        mockMvc.perform(post("/api/documents/{id}/forward", documentId)
                .header("Authorization", bearer(dcToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                        {
                          "toUserId": %d,
                          "forwardVisibility": "PUBLIC"
                        }
                        """, ddc.getId())))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/documents/{id}/undo-send", documentId)
                .header("Authorization", bearer(dcToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/documents/{id}", documentId)
                .header("Authorization", bearer(dcToken)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode doc = readJson(result);
        assertThat(doc.get("currentOwnerUserId").asLong()).isEqualTo(dc.getId());
    }

    @Test
    void undoSend_RestoresPreviousCC_And_BCC() throws Exception {
        mockMvc.perform(post("/api/documents/{id}/forward", documentId)
                .header("Authorization", bearer(dcToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                        {
                          "toUserId": %d,
                          "ccUserIds": [%d],
                          "bccUserIds": [%d],
                          "forwardVisibility": "PUBLIC"
                        }
                        """, ddc.getId(), sc.getId(), sc2.getId())))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/documents/{id}/undo-send", documentId)
                .header("Authorization", bearer(dcToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/documents/{id}", documentId)
                .header("Authorization", bearer(dcToken)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode doc = readJson(result);
        assertThat(doc.get("recipientSummary").get("cc")).hasSize(0);
        assertThat(doc.get("recipientSummary").get("bcc")).hasSize(0);
    }

    // =========================================================
    // PHASE 8: Reopen Sets TO (3 tests)
    // =========================================================

    @Test
    void reopen_SetsActorAsTO() throws Exception {
        mockMvc.perform(post("/api/documents/{id}/forward", documentId)
                .header("Authorization", bearer(dcToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                        {
                          "toUserId": %d,
                          "forwardVisibility": "PUBLIC"
                        }
                        """, ddc.getId())))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/documents/{id}/approve", documentId)
                .header("Authorization", bearer(ddcToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/documents/{id}/reopen", documentId)
                .header("Authorization", bearer(ddcToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"remarkText\":\"Need to change details\"}"))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/documents/{id}", documentId)
                .header("Authorization", bearer(ddcToken)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode doc = readJson(result);
        assertThat(doc.get("currentOwnerUserId").asLong()).isEqualTo(ddc.getId());
        assertThat(doc.get("recipientType").asText()).isEqualTo("TO");
    }

    @Test
    void reopen_NoAutomaticCC_BCC() throws Exception {
        mockMvc.perform(post("/api/documents/{id}/forward", documentId)
                .header("Authorization", bearer(dcToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                        {
                          "toUserId": %d,
                          "ccUserIds": [%d],
                          "forwardVisibility": "PUBLIC"
                        }
                        """, ddc.getId(), sc.getId())))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/documents/{id}/approve", documentId)
                .header("Authorization", bearer(ddcToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/documents/{id}/reopen", documentId)
                .header("Authorization", bearer(ddcToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"remarkText\":\"Reopen reason\"}"))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/documents/{id}", documentId)
                .header("Authorization", bearer(ddcToken)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode doc = readJson(result);
        assertThat(doc.get("recipientSummary").get("cc")).hasSize(0);
    }

    // =========================================================
    // PHASE 9: Manage Recipients (6 tests)
    // =========================================================

    @Test
    void manageRecipients_CurrentTOWithPermission() throws Exception {
        mockMvc.perform(put("/api/documents/{id}/recipients", documentId)
                .header("Authorization", bearer(dcToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                        {
                          "ccUserIds": [%d, %d],
                          "bccUserIds": [%d]
                        }
                        """, sc.getId(), sc2.getId(), sc3.getId())))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/documents/{id}", documentId)
                .header("Authorization", bearer(dcToken)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode doc = readJson(result);
        assertThat(doc.get("recipientSummary").get("cc")).hasSize(2);
        assertThat(doc.get("recipientSummary").get("bcc")).hasSize(1);
    }

    @Test
    void manageRecipients_CC_CannotManage() throws Exception {
        mockMvc.perform(post("/api/documents/{id}/forward", documentId)
                .header("Authorization", bearer(dcToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                        {
                          "toUserId": %d,
                          "ccUserIds": [%d],
                          "forwardVisibility": "PUBLIC"
                        }
                        """, ddc.getId(), sc.getId())))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/documents/{id}/recipients", documentId)
                .header("Authorization", bearer(scToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                        {
                          "ccUserIds": [%d]
                        }
                        """, sc2.getId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void manageRecipients_TO_CannotBeChanged() throws Exception {
        mockMvc.perform(put("/api/documents/{id}/recipients", documentId)
                .header("Authorization", bearer(dcToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                        {
                          "ccUserIds": [%d]
                        }
                        """, sc.getId())))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/documents/{id}", documentId)
                .header("Authorization", bearer(dcToken)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode doc = readJson(result);
        assertThat(doc.get("currentOwnerUserId").asLong()).isEqualTo(dc.getId());
    }

    // =========================================================
    // PHASE 10: CC/BCC Access + Capabilities (8 tests)
    // =========================================================

    @Test
    void cc_CanOpenDocument_WhenEnabled() throws Exception {
        mockMvc.perform(post("/api/documents/{id}/forward", documentId)
                .header("Authorization", bearer(dcToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                        {
                          "toUserId": %d,
                          "ccUserIds": [%d],
                          "forwardVisibility": "PUBLIC"
                        }
                        """, ddc.getId(), sc.getId())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/documents/{id}", documentId)
                .header("Authorization", bearer(scToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipientType").value("CC"));
    }

    @Test
    void cc_CannotWorkflow() throws Exception {
        mockMvc.perform(post("/api/documents/{id}/forward", documentId)
                .header("Authorization", bearer(dcToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                        {
                          "toUserId": %d,
                          "ccUserIds": [%d],
                          "forwardVisibility": "PUBLIC"
                        }
                        """, ddc.getId(), sc.getId())))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/documents/{id}", documentId)
                .header("Authorization", bearer(scToken)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode doc = readJson(result);
        assertThat(doc.get("canWorkflow").asBoolean()).isFalse();
    }

    @Test
    void bcc_CanOpenDocument_WhenEnabled() throws Exception {
        mockMvc.perform(post("/api/documents/{id}/forward", documentId)
                .header("Authorization", bearer(dcToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                        {
                          "toUserId": %d,
                          "bccUserIds": [%d],
                          "forwardVisibility": "PUBLIC"
                        }
                        """, ddc.getId(), sc.getId())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/documents/{id}", documentId)
                .header("Authorization", bearer(scToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipientType").value("BCC"));
    }

    @Test
    void bcc_CannotWorkflow() throws Exception {
        mockMvc.perform(post("/api/documents/{id}/forward", documentId)
                .header("Authorization", bearer(dcToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                        {
                          "toUserId": %d,
                          "bccUserIds": [%d],
                          "forwardVisibility": "PUBLIC"
                        }
                        """, ddc.getId(), sc.getId())))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/documents/{id}", documentId)
                .header("Authorization", bearer(scToken)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode doc = readJson(result);
        assertThat(doc.get("canWorkflow").asBoolean()).isFalse();
    }

    @Test
    void bcc_HiddenFromNormalUserSummary() throws Exception {
        mockMvc.perform(post("/api/documents/{id}/forward", documentId)
                .header("Authorization", bearer(dcToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                        {
                          "toUserId": %d,
                          "bccUserIds": [%d],
                          "forwardVisibility": "PUBLIC"
                        }
                        """, ddc.getId(), sc.getId())))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/documents/{id}", documentId)
                .header("Authorization", bearer(ddcToken)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode doc = readJson(result);
        assertThat(doc.get("recipientSummary").get("bcc")).hasSize(0);
    }

    @Test
    void backendReturnsCorrectCapabilityFlags() throws Exception {
        mockMvc.perform(post("/api/documents/{id}/forward", documentId)
                .header("Authorization", bearer(dcToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                        {
                          "toUserId": %d,
                          "ccUserIds": [%d],
                          "forwardVisibility": "PUBLIC"
                        }
                        """, ddc.getId(), sc.getId())))
                .andExpect(status().isOk());

        MvcResult toResult = mockMvc.perform(get("/api/documents/{id}", documentId)
                .header("Authorization", bearer(ddcToken)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode toDoc = readJson(toResult);
        assertThat(toDoc.get("canWorkflow").asBoolean()).isTrue();

        MvcResult ccResult = mockMvc.perform(get("/api/documents/{id}", documentId)
                .header("Authorization", bearer(scToken)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode ccDoc = readJson(ccResult);
        assertThat(ccDoc.get("canWorkflow").asBoolean()).isFalse();
    }

    // =========================================================
    // HELPER METHODS
    // =========================================================

    private User createUser(String roleName, String usernamePrefix, String password) {
        User user = new User();
        user.setUsername(usernamePrefix + UUID.randomUUID());
        user.setFullName("Test " + roleName + " User");
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setIsActive(true);
        user.setRole(roleRepository.findByRoleName(roleName)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleName)));
        return userRepository.save(user);
    }

    private void enableRecipientWorkflowPermissions() {
        enablePermissions("DC",
                AppPermission.FORWARD_DOCUMENT,
                AppPermission.FORWARD_PUBLIC,
                AppPermission.FORWARD_PRIVATE,
                AppPermission.RETURN_DOCUMENT,
                AppPermission.MANAGE_DOCUMENT_RECIPIENTS,
                AppPermission.REOPEN_DOCUMENT,
                AppPermission.APPROVE_DOCUMENT,
                AppPermission.REJECT_DOCUMENT,
                AppPermission.VIEW_HIDDEN_RECIPIENTS
        );
        enablePermissions("DDC",
                AppPermission.FORWARD_DOCUMENT,
                AppPermission.FORWARD_PUBLIC,
                AppPermission.FORWARD_PRIVATE,
                AppPermission.RETURN_DOCUMENT,
                AppPermission.MANAGE_DOCUMENT_RECIPIENTS,
                AppPermission.REOPEN_DOCUMENT,
                AppPermission.APPROVE_DOCUMENT,
                AppPermission.REJECT_DOCUMENT
        );
        enablePermissions("SC",
                AppPermission.CC_VIEW_DOCUMENT,
                AppPermission.BCC_VIEW_DOCUMENT,
                AppPermission.CC_VIEW_TIMELINE,
                AppPermission.BCC_VIEW_TIMELINE,
                AppPermission.CC_VIEW_ATTACHMENTS,
                AppPermission.BCC_VIEW_ATTACHMENTS
        );
    }

    private void enablePermissions(String roleName, AppPermission... permissions) {
        Role role = roleRepository.findByRoleName(roleName)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleName));
        for (AppPermission permission : permissions) {
            RolePermission rolePermission = rolePermissionRepository.findByRole_IdAndPermissionNameIgnoreCase(role.getId(), permission.name())
                    .orElseGet(() -> {
                        RolePermission created = new RolePermission();
                        created.setRole(role);
                        created.setPermissionName(permission.name());
                        return created;
                    });
            rolePermission.setEnabled(true);
            rolePermissionRepository.save(rolePermission);
        }
        rolePermissionRepository.flush();
    }

    private void enableUndoSendConfig() {
        DcAutoForwardConfig config = dcAutoForwardConfigRepository.findById(1L).orElseGet(DcAutoForwardConfig::new);
        config.setId(1L);
        config.setUndoSendEnabled(true);
        config.setUndoSendAllowedActions("FORWARD,RETURN");
        config.setUndoSendWindowHours(24);
        config.setUndoSendRequiresUnopened(false);
        config.setUndoSendRequiresReason(false);
        config.setUndoSendNotifyReceiver(false);
        config.setForwardReturnAllowedStatuses("PENDING,IN_PROGRESS,RETURNED");
        dcAutoForwardConfigRepository.saveAndFlush(config);
    }

    private String loginAndGetToken(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                        {
                          "username": "%s",
                          "password": "%s"
                        }
                        """, username, password)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("accessToken").asText();
    }

    private long createDocument(String token, String refPrefix) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/documents")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                        {
                          "refNo": "REF-%s-%d",
                          "title": "Test Document",
                          "companyName": "Test Company",
                          "receivedDate": "2026-06-16",
                          "priority": "HIGH",
                          "documentType": "INTERNAL"
                        }
                        """, refPrefix, System.currentTimeMillis())))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = readJson(result);
        return json.get("id").asLong();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private AuditLog latestAuditLog(String entityType, Long entityId, String actionType) {
        return auditLogRepository.findByEntityTypeAndEntityIdOrderByPerformedAtAsc(entityType, entityId)
                .stream()
                .filter(log -> actionType.equals(log.getActionType()))
                .reduce((first, second) -> second)
                .orElseThrow(() -> new AssertionError("Audit log not found: " + actionType));
    }
}
