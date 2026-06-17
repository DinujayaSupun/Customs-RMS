package lk.customs.rms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lk.customs.rms.entity.AuditLog;
import lk.customs.rms.entity.Document;
import lk.customs.rms.entity.DcAutoForwardConfig;
import lk.customs.rms.entity.Role;
import lk.customs.rms.entity.RolePermission;
import lk.customs.rms.entity.User;
import lk.customs.rms.enums.AppPermission;
import lk.customs.rms.repository.DcAutoForwardConfigRepository;
import lk.customs.rms.repository.DocumentRepository;
import lk.customs.rms.repository.AuditLogRepository;
import lk.customs.rms.repository.RolePermissionRepository;
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
import java.net.URI;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class AttachmentAndVisibilityIntegrationTests {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private RolePermissionRepository rolePermissionRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

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
    void issuedOwnerCannotUploadOrDeleteAttachments() throws Exception {
        String password = "AttachIssue123";
        User admin = createUser("ADMIN", "attach-issued-", password);
        String adminToken = loginAndGetToken(admin.getUsername(), password);
        long documentId = createDocument(adminToken, "attach-issued-doc");

        long attachmentId = uploadAttachment(documentId, adminToken, "before-issue.txt", "before issue").get("id").asLong();

        approveAndIssue(documentId, adminToken);

        MockMultipartFile issuedUpload = textFile("after-issue.txt", "after issue");

        mockMvc.perform(multipart("/api/documents/{documentId}/attachments", documentId)
                        .file(issuedUpload)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cannot upload attachments after document is ISSUED."));

        mockMvc.perform(delete("/api/attachments/{attachmentId}", attachmentId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cannot delete attachments after document is ISSUED."));
    }

    @Test
    void uploadingNewAttachmentMarksLatestAndDeletingLatestRestoresPreviousLatest() throws Exception {
        String password = "AttachLatest123";
        User admin = createUser("ADMIN", "attach-latest-", password);
        String adminToken = loginAndGetToken(admin.getUsername(), password);
        long documentId = createDocument(adminToken, "attach-latest-doc");

        JsonNode v1 = uploadAttachment(documentId, adminToken, "v1.txt", "first");
        JsonNode v2 = uploadAttachment(documentId, adminToken, "v2.txt", "second");

        MvcResult listedBeforeDelete = mockMvc.perform(get("/api/documents/{documentId}/attachments", documentId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode beforeDeleteJson = readJson(listedBeforeDelete);
        assertThat(beforeDeleteJson).hasSize(2);
        assertThat(beforeDeleteJson.get(0).get("versionNo").asInt()).isEqualTo(1);
        assertThat(beforeDeleteJson.get(0).get("isLatest").asBoolean()).isFalse();
        assertThat(beforeDeleteJson.get(1).get("versionNo").asInt()).isEqualTo(2);
        assertThat(beforeDeleteJson.get(1).get("isLatest").asBoolean()).isTrue();

        mockMvc.perform(delete("/api/attachments/{attachmentId}", v2.get("id").asLong())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNoContent());

        MvcResult listedAfterDelete = mockMvc.perform(get("/api/documents/{documentId}/attachments", documentId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode afterDeleteJson = readJson(listedAfterDelete);
        assertThat(afterDeleteJson).hasSize(1);
        assertThat(afterDeleteJson.get(0).get("id").asLong()).isEqualTo(v1.get("id").asLong());
        assertThat(afterDeleteJson.get(0).get("versionNo").asInt()).isEqualTo(1);
        assertThat(afterDeleteJson.get(0).get("isLatest").asBoolean()).isTrue();
    }

    @Test
    void uploadRequiresCurrentOwnerAndUploadPermission() throws Exception {
        String password = "AttachUpload123";
        User creator = createUser("ADMIN", "attach-upload-creator-", password);
        User owner = createUser("SC", "attach-upload-owner-", password);
        User outsider = createUser("ADMIN", "attach-upload-outsider-", password);

        String creatorToken = loginAndGetToken(creator.getUsername(), password);
        String ownerToken = loginAndGetToken(owner.getUsername(), password);
        String outsiderToken = loginAndGetToken(outsider.getUsername(), password);
        long documentId = createDocumentForOwner(creatorToken, owner.getId(), "attach-upload-permission-doc");

        Map<Long, Boolean> originalScPermissions = snapshotRolePermissions("SC", AppPermission.UPLOAD_ATTACHMENT);

        try {
            mockMvc.perform(multipart("/api/documents/{documentId}/attachments", documentId)
                            .file(textFile("outsider.txt", "outsider upload"))
                            .header("Authorization", bearer(outsiderToken)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Only the current owner can upload attachments."));

            setRolePermission("SC", AppPermission.UPLOAD_ATTACHMENT, false);

            mockMvc.perform(multipart("/api/documents/{documentId}/attachments", documentId)
                            .file(textFile("blocked.txt", "blocked upload"))
                            .header("Authorization", bearer(ownerToken)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("You are not allowed to upload attachments."));

            setRolePermission("SC", AppPermission.UPLOAD_ATTACHMENT, true);

            mockMvc.perform(multipart("/api/documents/{documentId}/attachments", documentId)
                            .file(textFile("allowed.txt", "allowed upload"))
                            .header("Authorization", bearer(ownerToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.fileName").value("allowed.txt"));
        } finally {
            restoreRolePermissions(originalScPermissions);
        }
    }

    @Test
    void deleteRequiresCurrentOwnerAndDeletePermission() throws Exception {
        String password = "AttachDelete123";
        User creator = createUser("ADMIN", "attach-delete-creator-", password);
        User owner = createUser("SC", "attach-delete-owner-", password);
        User outsider = createUser("ADMIN", "attach-delete-outsider-", password);

        String creatorToken = loginAndGetToken(creator.getUsername(), password);
        String ownerToken = loginAndGetToken(owner.getUsername(), password);
        String outsiderToken = loginAndGetToken(outsider.getUsername(), password);
        long documentId = createDocumentForOwner(creatorToken, owner.getId(), "attach-delete-permission-doc");
        long outsiderBlockedAttachmentId = uploadAttachment(documentId, ownerToken, "outsider-blocked.txt", "keep").get("id").asLong();

        mockMvc.perform(delete("/api/attachments/{attachmentId}", outsiderBlockedAttachmentId)
                        .header("Authorization", bearer(outsiderToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only the current owner can delete attachments."));

        long permissionBlockedAttachmentId = uploadAttachment(documentId, ownerToken, "permission-blocked.txt", "delete check").get("id").asLong();
        Map<Long, Boolean> originalScPermissions = snapshotRolePermissions("SC", AppPermission.DELETE_ATTACHMENT);

        try {
            setRolePermission("SC", AppPermission.DELETE_ATTACHMENT, false);

            mockMvc.perform(delete("/api/attachments/{attachmentId}", permissionBlockedAttachmentId)
                            .header("Authorization", bearer(ownerToken)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("You are not allowed to delete attachments."));

            setRolePermission("SC", AppPermission.DELETE_ATTACHMENT, true);

            mockMvc.perform(delete("/api/attachments/{attachmentId}", permissionBlockedAttachmentId)
                            .header("Authorization", bearer(ownerToken)))
                    .andExpect(status().isNoContent());
        } finally {
            restoreRolePermissions(originalScPermissions);
        }
    }

    @Test
    void fileHistoryAndDownloadRequireOwnerOrViewAllHistoryPermission() throws Exception {
        String password = "AttachHistory123";
        User creator = createUser("ADMIN", "attach-history-creator-", password);
        User owner = createUser("SC", "attach-history-owner-", password);
        User outsider = createUser("PMA", "attach-history-outsider-", password);
        User historyViewer = createUser("DC", "attach-history-viewer-", password);

        String creatorToken = loginAndGetToken(creator.getUsername(), password);
        String ownerToken = loginAndGetToken(owner.getUsername(), password);
        String outsiderToken = loginAndGetToken(outsider.getUsername(), password);
        String historyViewerToken = loginAndGetToken(historyViewer.getUsername(), password);
        long documentId = createDocumentForOwner(creatorToken, owner.getId(), "attach-history-doc");
        long attachmentId = uploadAttachment(documentId, ownerToken, "history.txt", "history body").get("id").asLong();

        mockMvc.perform(get("/api/documents/{documentId}/attachments", documentId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(attachmentId));

        mockMvc.perform(get("/api/documents/{documentId}/attachments", documentId)
                        .header("Authorization", bearer(outsiderToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You are not allowed to view file history for this document."));

        mockMvc.perform(get("/api/attachments/{attachmentId}/download", attachmentId)
                        .header("Authorization", bearer(outsiderToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You are not allowed to view file history for this document."));

        mockMvc.perform(get("/api/documents/{documentId}/attachments", documentId)
                        .header("Authorization", bearer(historyViewerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(attachmentId));

        mockMvc.perform(get("/api/attachments/{attachmentId}/download", attachmentId)
                        .header("Authorization", bearer(historyViewerToken)))
                .andExpect(status().isOk());
    }

    @Test
    void currentReportAtUserCanDeleteDocumentWithoutDeleteDocumentPermission() throws Exception {
        String password = "DocDelete123";
        User owner = createUser("PMA", "doc-delete-owner-no-perm-", password);
        String ownerToken = loginAndGetToken(owner.getUsername(), password);
        long documentId = createDocument(ownerToken, "doc-delete-owner-no-perm");

        Map<Long, Boolean> originalPmaPermissions = snapshotRolePermissions(
                "PMA",
                AppPermission.DELETE_DOCUMENT,
                AppPermission.DELETE_ANY_DOCUMENT
        );

        try {
            setRolePermission("PMA", AppPermission.DELETE_DOCUMENT, false);
            setRolePermission("PMA", AppPermission.DELETE_ANY_DOCUMENT, false);

            mockMvc.perform(delete("/api/documents/{documentId}", documentId)
                            .header("Authorization", bearer(ownerToken)))
                    .andExpect(status().isNoContent());

            assertThat(documentRepository.findByIdAndDeletedFalse(documentId)).isEmpty();
        } finally {
            restoreRolePermissions(originalPmaPermissions);
        }
    }

    @Test
    void documentDeleteSoftDeletesDocumentAndRecordsAuditLog() throws Exception {
        String password = "DocDelete123";
        User admin = createUser("ADMIN", "doc-delete-admin-", password);
        String adminToken = loginAndGetToken(admin.getUsername(), password);
        long documentId = createDocument(adminToken, "doc-delete-soft");

        mockMvc.perform(delete("/api/documents/{documentId}", documentId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNoContent());

        Document deleted = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalStateException("Document not found: " + documentId));
        assertThat(deleted.isDeleted()).isTrue();
        assertThat(deleted.getDeletedAt()).isNotNull();
        assertThat(deleted.getDeletedByUserId()).isEqualTo(admin.getId());
        assertThat(documentRepository.findByIdAndDeletedFalse(documentId)).isEmpty();

        mockMvc.perform(get("/api/documents/{documentId}", documentId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/documents/{documentId}", documentId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound());

        AuditLog deleteLog = auditLogRepository.findByEntityTypeAndEntityIdOrderByPerformedAtAsc("DOCUMENT", documentId)
                .stream()
                .filter(log -> "DELETE".equals(log.getActionType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Delete audit log not found."));
        assertThat(deleteLog.getPerformedByUserId()).isEqualTo(admin.getId());
        assertThat(deleteLog.getMessage()).contains(deleted.getRefNo(), deleted.getTitle(), admin.getFullName());

        JsonNode details = objectMapper.readTree(deleteLog.getDetailsJson());
        assertThat(details.get("documentId").asLong()).isEqualTo(documentId);
        assertThat(details.get("refNo").asText()).isEqualTo(deleted.getRefNo());
        assertThat(details.get("title").asText()).isEqualTo(deleted.getTitle());
        assertThat(details.get("status").asText()).isEqualTo("PENDING");
        assertThat(details.get("priority").asText()).isEqualTo("HIGH");
        assertThat(details.get("currentOwnerUserId").asLong()).isEqualTo(admin.getId());
        assertThat(details.get("currentOwnerName").asText()).isEqualTo(admin.getFullName());
        assertThat(details.get("deletedByUserId").asLong()).isEqualTo(admin.getId());
        assertThat(details.get("deletedByName").asText()).isEqualTo(admin.getFullName());
    }

    @Test
    void documentDeleteRequiresCurrentReportAtUserUnlessDeleteAnyDocumentPermissionIsEnabled() throws Exception {
        String password = "DocDelete123";
        User owner = createUser("PMA", "doc-delete-owner-", password);
        User outsider = createUser("PMA", "doc-delete-outsider-", password);
        String ownerToken = loginAndGetToken(owner.getUsername(), password);
        String outsiderToken = loginAndGetToken(outsider.getUsername(), password);

        long ownedDocumentId = createDocumentForOwner(ownerToken, owner.getId(), "doc-delete-owner-ok");
        long otherOwnedDocumentId = createDocumentForOwner(ownerToken, owner.getId(), "doc-delete-owner-only");

        Map<Long, Boolean> originalPmaPermissions = snapshotRolePermissions(
                "PMA",
                AppPermission.DELETE_DOCUMENT,
                AppPermission.DELETE_ANY_DOCUMENT
        );

        try {
            setRolePermission("PMA", AppPermission.DELETE_DOCUMENT, false);
            setRolePermission("PMA", AppPermission.DELETE_ANY_DOCUMENT, false);

            mockMvc.perform(delete("/api/documents/{documentId}", otherOwnedDocumentId)
                            .header("Authorization", bearer(outsiderToken)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Only the current Report At user can delete this document."));
            assertThat(documentRepository.findByIdAndDeletedFalse(otherOwnedDocumentId)).isPresent();

            mockMvc.perform(delete("/api/documents/{documentId}", ownedDocumentId)
                            .header("Authorization", bearer(ownerToken)))
                    .andExpect(status().isNoContent());
            assertThat(documentRepository.findByIdAndDeletedFalse(ownedDocumentId)).isEmpty();

            setRolePermission("PMA", AppPermission.DELETE_ANY_DOCUMENT, true);

            mockMvc.perform(delete("/api/documents/{documentId}", otherOwnedDocumentId)
                            .header("Authorization", bearer(outsiderToken)))
                    .andExpect(status().isNoContent());
            assertThat(documentRepository.findByIdAndDeletedFalse(otherOwnedDocumentId)).isEmpty();
        } finally {
            restoreRolePermissions(originalPmaPermissions);
        }
    }

    @Test
    void documentResponseExposesServerSideDeleteCapability() throws Exception {
        String password = "DocDelete123";
        User owner = createUser("PMA", "doc-delete-cap-owner-", password);
        User outsider = createUser("PMA", "doc-delete-cap-outsider-", password);
        String ownerToken = loginAndGetToken(owner.getUsername(), password);
        String outsiderToken = loginAndGetToken(outsider.getUsername(), password);
        long documentId = createDocumentForOwner(ownerToken, owner.getId(), "doc-delete-capability");
        setDocumentVisibility(documentId, "PUBLIC");

        Map<Long, Boolean> originalPmaPermissions = snapshotRolePermissions(
                "PMA",
                AppPermission.VIEW_PUBLIC_DOCUMENT,
                AppPermission.DELETE_ANY_DOCUMENT
        );

        try {
            setRolePermission("PMA", AppPermission.VIEW_PUBLIC_DOCUMENT, true);
            setRolePermission("PMA", AppPermission.DELETE_ANY_DOCUMENT, false);

            mockMvc.perform(get("/api/documents/{documentId}", documentId)
                            .header("Authorization", bearer(ownerToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.canDelete").value(true));

            mockMvc.perform(get("/api/documents/{documentId}", documentId)
                            .header("Authorization", bearer(outsiderToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.canDelete").value(false));

            setRolePermission("PMA", AppPermission.DELETE_ANY_DOCUMENT, true);

            mockMvc.perform(get("/api/documents/{documentId}", documentId)
                            .header("Authorization", bearer(outsiderToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.canDelete").value(true));
        } finally {
            restoreRolePermissions(originalPmaPermissions);
        }
    }

    @Test
    void attachmentDownloadUsesScopedDownloadTokenAndRejectsAccessTokenQuery() throws Exception {
        String password = "AttachToken123";
        User owner = createUser("ADMIN", "attach-token-owner-", password);
        String ownerToken = loginAndGetToken(owner.getUsername(), password);
        long documentId = createDocument(ownerToken, "attach-token-doc");
        long attachmentId = uploadAttachment(documentId, ownerToken, "token-download.txt", "download body").get("id").asLong();

        mockMvc.perform(get("/api/attachments/{attachmentId}/download", attachmentId)
                        .param("access_token", ownerToken))
                .andExpect(status().isForbidden());

        MvcResult tokenResult = mockMvc.perform(post("/api/attachments/{attachmentId}/download-token", attachmentId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.containsString("download_token=")))
                .andExpect(jsonPath("$.expiresInSeconds").value(120))
                .andReturn();

        String downloadToken = queryParam(readJson(tokenResult).get("url").asText(), "download_token");
        assertThat(downloadToken).isNotBlank();

        mockMvc.perform(get("/api/attachments/{attachmentId}/download", attachmentId)
                        .param("download_token", downloadToken))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo("download body"));
    }

    @Test
    void userWithOnlyPublicDocumentPermissionListsPublicDocButNotPrivateDoc() throws Exception {
        String password = "Visibility123";
        User admin = createUser("ADMIN", "visibility-admin-", password);
        User viewer = createUser("SC", "visibility-viewer-", password);

        String adminToken = loginAndGetToken(admin.getUsername(), password);
        String viewerToken = loginAndGetToken(viewer.getUsername(), password);

        long publicDocumentId = createDocument(adminToken, "public-visible-doc");
        long privateDocumentId = createDocument(adminToken, "private-hidden-doc");
        setDocumentVisibility(publicDocumentId, "PUBLIC");
        setDocumentVisibility(privateDocumentId, "PRIVATE");

        Map<Long, Boolean> originalScPermissions = snapshotRolePermissions("SC",
                AppPermission.VIEW_PUBLIC_DOCUMENT,
                AppPermission.VIEW_PRIVATE_DOCUMENT,
                AppPermission.VIEW_OWN_CREATED_DOCUMENTS,
                AppPermission.VIEW_ALL_DOCUMENTS
        );

        try {
            setRolePermission("SC", AppPermission.VIEW_PUBLIC_DOCUMENT, true);
            setRolePermission("SC", AppPermission.VIEW_PRIVATE_DOCUMENT, false);
            setRolePermission("SC", AppPermission.VIEW_OWN_CREATED_DOCUMENTS, false);
            setRolePermission("SC", AppPermission.VIEW_ALL_DOCUMENTS, false);

            MvcResult listResult = mockMvc.perform(get("/api/documents")
                            .header("Authorization", bearer(viewerToken))
                            .param("size", "50"))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode content = readJson(listResult).get("content");
            List<String> refNos = java.util.stream.StreamSupport.stream(content.spliterator(), false)
                    .map(node -> node.get("refNo").asText())
                    .toList();

            String publicRefNo = documentRefNo(publicDocumentId);
            String privateRefNo = documentRefNo(privateDocumentId);

            assertThat(refNos).contains(publicRefNo);
            assertThat(refNos).doesNotContain(privateRefNo);
        } finally {
            restoreRolePermissions(originalScPermissions);
        }
    }

    @Test
    void userWithOnlyPublicDocumentPermissionCanOpenPublicDocButNotPrivateDoc() throws Exception {
        String password = "Visibility123";
        User admin = createUser("ADMIN", "visibility-open-admin-", password);
        User viewer = createUser("SC", "visibility-open-viewer-", password);

        String adminToken = loginAndGetToken(admin.getUsername(), password);
        String viewerToken = loginAndGetToken(viewer.getUsername(), password);

        long publicDocumentId = createDocument(adminToken, "public-open-doc");
        long privateDocumentId = createDocument(adminToken, "private-open-doc");
        setDocumentVisibility(publicDocumentId, "PUBLIC");
        setDocumentVisibility(privateDocumentId, "PRIVATE");

        Map<Long, Boolean> originalScPermissions = snapshotRolePermissions("SC",
                AppPermission.VIEW_PUBLIC_DOCUMENT,
                AppPermission.VIEW_PRIVATE_DOCUMENT,
                AppPermission.VIEW_OWN_CREATED_DOCUMENTS,
                AppPermission.VIEW_ALL_DOCUMENTS
        );

        try {
            setRolePermission("SC", AppPermission.VIEW_PUBLIC_DOCUMENT, true);
            setRolePermission("SC", AppPermission.VIEW_PRIVATE_DOCUMENT, false);
            setRolePermission("SC", AppPermission.VIEW_OWN_CREATED_DOCUMENTS, false);
            setRolePermission("SC", AppPermission.VIEW_ALL_DOCUMENTS, false);

            mockMvc.perform(get("/api/documents/{id}", publicDocumentId)
                            .header("Authorization", bearer(viewerToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(publicDocumentId));

            mockMvc.perform(get("/api/documents/{id}", privateDocumentId)
                            .header("Authorization", bearer(viewerToken)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("You are not allowed to view this private document."));
        } finally {
            restoreRolePermissions(originalScPermissions);
        }
    }

    @Test
    void copiedRecipientsCanViewAndManageOwnAttachmentsWhileReturnRestoresPreviousRecipientSet() throws Exception {
        String password = "Recipients123";
        User sender = createUser("ADMIN", "recipient-sender-", password);
        User toUser = createUser("DDC", "recipient-to-", password);
        User ccUser = createUser("PMA", "recipient-cc-", password);
        User replacementCcUser = createUser("PMA", "recipient-cc-replace-", password);
        User bccUser = createUser("PMA", "recipient-bcc-", password);

        String senderToken = loginAndGetToken(sender.getUsername(), password);
        String toToken = loginAndGetToken(toUser.getUsername(), password);
        String ccToken = loginAndGetToken(ccUser.getUsername(), password);
        String replacementCcToken = loginAndGetToken(replacementCcUser.getUsername(), password);
        String bccToken = loginAndGetToken(bccUser.getUsername(), password);

        long documentId = createDocument(senderToken, "recipient-flow");
        long mainAttachmentId = uploadAttachment(documentId, senderToken, "main.txt", "main").get("id").asLong();

        Map<Long, Boolean> originalPmaPermissions = snapshotRolePermissions(
                "PMA",
                AppPermission.VIEW_PRIVATE_DOCUMENT,
                AppPermission.VIEW_PUBLIC_DOCUMENT,
                AppPermission.VIEW_ALL_DOCUMENTS,
                AppPermission.CC_VIEW_DOCUMENT,
                AppPermission.CC_VIEW_ATTACHMENTS,
                AppPermission.CC_UPLOAD_ATTACHMENTS,
                AppPermission.CC_DELETE_OWN_ATTACHMENTS,
                AppPermission.BCC_VIEW_DOCUMENT,
                AppPermission.BCC_VIEW_ATTACHMENTS,
                AppPermission.UPLOAD_ATTACHMENT,
                AppPermission.DELETE_ATTACHMENT
        );
        Map<Long, Boolean> originalDdcPermissions = snapshotRolePermissions(
                "DDC",
                AppPermission.MANAGE_DOCUMENT_RECIPIENTS
        );

        try {
            setRolePermission("DDC", AppPermission.MANAGE_DOCUMENT_RECIPIENTS, true);
            setRolePermission("PMA", AppPermission.VIEW_PRIVATE_DOCUMENT, false);
            setRolePermission("PMA", AppPermission.VIEW_PUBLIC_DOCUMENT, false);
            setRolePermission("PMA", AppPermission.VIEW_ALL_DOCUMENTS, false);
            setRolePermission("PMA", AppPermission.CC_VIEW_DOCUMENT, true);
            setRolePermission("PMA", AppPermission.CC_VIEW_ATTACHMENTS, true);
            setRolePermission("PMA", AppPermission.CC_UPLOAD_ATTACHMENTS, true);
            setRolePermission("PMA", AppPermission.CC_DELETE_OWN_ATTACHMENTS, true);
            setRolePermission("PMA", AppPermission.BCC_VIEW_DOCUMENT, true);
            setRolePermission("PMA", AppPermission.BCC_VIEW_ATTACHMENTS, true);
            setRolePermission("PMA", AppPermission.UPLOAD_ATTACHMENT, true);
            setRolePermission("PMA", AppPermission.DELETE_ATTACHMENT, true);

            mockMvc.perform(post("/api/documents/{documentId}/forward", documentId)
                            .header("Authorization", bearer(senderToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "toUserId": %d,
                                      "ccUserIds": [%d],
                                      "bccUserIds": [%d],
                                      "forwardVisibility": "PRIVATE",
                                      "remarkText": "Forward with copied recipients"
                                    }
                                    """.formatted(toUser.getId(), ccUser.getId(), bccUser.getId())))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/documents/{documentId}", documentId)
                            .header("Authorization", bearer(ccToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.recipientType").value("CC"))
                    .andExpect(jsonPath("$.canWorkflow").value(false))
                    .andExpect(jsonPath("$.canUploadAttachment").value(true))
                    .andExpect(jsonPath("$.recipientSummary.bcc").isEmpty());

            mockMvc.perform(get("/api/documents/{documentId}", documentId)
                            .header("Authorization", bearer(bccToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.recipientType").value("BCC"))
                    .andExpect(jsonPath("$.recipientSummary.bcc[0].name").value("you"));

            MvcResult recipientUpdateResult = mockMvc.perform(put("/api/documents/{documentId}/recipients", documentId)
                            .header("Authorization", bearer(toToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "ccUserIds": [%d],
                                      "bccUserIds": [%d]
                                    }
                                    """.formatted(replacementCcUser.getId(), bccUser.getId())))
                    .andReturn();
            assertThat(recipientUpdateResult.getResponse().getStatus())
                    .as(recipientUpdateResult.getResponse().getContentAsString())
                    .isEqualTo(200);
            JsonNode recipientUpdateJson = objectMapper.readTree(recipientUpdateResult.getResponse().getContentAsString());
            assertThat(recipientUpdateJson.at("/recipientSummary/cc/0/userId").asLong()).isEqualTo(replacementCcUser.getId());
            assertThat(recipientUpdateJson.at("/recipientSummary/bcc").isArray()).isTrue();
            assertThat(recipientUpdateJson.at("/recipientSummary/bcc").size()).isZero();

            mockMvc.perform(get("/api/documents/{documentId}", documentId)
                            .header("Authorization", bearer(replacementCcToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.recipientType").value("CC"));

            mockMvc.perform(post("/api/documents/{documentId}/forward", documentId)
                            .header("Authorization", bearer(ccToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "toUserId": %d,
                                      "forwardVisibility": "PRIVATE"
                                    }
                                    """.formatted(sender.getId())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Only the current owner can forward this document."));

            long ccAttachmentId = uploadAttachment(documentId, replacementCcToken, "cc-owned.txt", "cc").get("id").asLong();
            JsonNode uploadAuditDetails = objectMapper.readTree(latestAuditLog("ATTACHMENT", ccAttachmentId, "UPLOAD").getDetailsJson());
            assertThat(uploadAuditDetails.get("actorRecipientType").asText()).isEqualTo("CC");

            mockMvc.perform(delete("/api/attachments/{attachmentId}", mainAttachmentId)
                            .header("Authorization", bearer(replacementCcToken)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Copied recipients can delete only their own attachments."));

            mockMvc.perform(delete("/api/attachments/{attachmentId}", ccAttachmentId)
                            .header("Authorization", bearer(replacementCcToken)))
                    .andExpect(status().isNoContent());
            JsonNode deleteAuditDetails = objectMapper.readTree(latestAuditLog("ATTACHMENT", ccAttachmentId, "DELETE").getDetailsJson());
            assertThat(deleteAuditDetails.get("actorRecipientType").asText()).isEqualTo("CC");

            mockMvc.perform(post("/api/documents/{documentId}/return", documentId)
                            .header("Authorization", bearer(toToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "toUserId": %d,
                                      "remarkText": "Return to previous set"
                                    }
                                    """.formatted(sender.getId())))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/documents/{documentId}", documentId)
                            .header("Authorization", bearer(senderToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.recipientType").value("TO"))
                    .andExpect(jsonPath("$.recipientSummary.to[0].currentUser").value(true));
        } finally {
            restoreRolePermissions(originalPmaPermissions);
            restoreRolePermissions(originalDdcPermissions);
        }
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

    private long createDocument(String token, String refPrefix) throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/documents")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(documentPayload(refPrefix)))
                .andExpect(status().isCreated())
                .andReturn();

        return readJson(createResult).get("id").asLong();
    }

    private long createDocumentForOwner(String creatorToken, Long ownerUserId, String refPrefix) throws Exception {
        long documentId = createDocument(creatorToken, refPrefix);
        Document document = documentRepository.findByIdAndDeletedFalse(documentId)
                .orElseThrow(() -> new IllegalStateException("Document not found: " + documentId));
        document.setCurrentOwnerUserId(ownerUserId);
        documentRepository.saveAndFlush(document);
        return documentId;
    }

    private String documentPayload(String refPrefix) {
        return """
                {
                  "refNo": "%s-%s",
                  "title": "Attachment Visibility Test Document",
                  "receivedDate": "%s",
                  "companyName": "Integration Co",
                  "priority": "HIGH"
                }
                """.formatted(refPrefix, UUID.randomUUID(), LocalDate.now());
    }

    private JsonNode uploadAttachment(long documentId, String token, String fileName, String body) throws Exception {
        MockMultipartFile file = textFile(fileName, body);
        MvcResult uploadResult = mockMvc.perform(multipart("/api/documents/{documentId}/attachments", documentId)
                        .file(file)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn();

        return readJson(uploadResult);
    }

    private MockMultipartFile textFile(String fileName, String body) {
        return new MockMultipartFile(
                "file",
                fileName,
                MediaType.TEXT_PLAIN_VALUE,
                body.getBytes(StandardCharsets.UTF_8)
        );
    }

    private void approveAndIssue(long documentId, String token) throws Exception {
        mockMvc.perform(post("/api/documents/{id}/approve", documentId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remarkText\":\"Approved before issue\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/documents/{id}/issue", documentId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remarkText\":\"Issued before attachment restriction check\"}"))
                .andExpect(status().isOk());
    }

    private void setDocumentVisibility(long documentId, String visibility) {
        Document document = documentRepository.findByIdAndDeletedFalse(documentId)
                .orElseThrow(() -> new IllegalStateException("Document not found: " + documentId));
        document.setVisibility(visibility);
        documentRepository.saveAndFlush(document);
    }

    private String documentRefNo(long documentId) {
        return documentRepository.findByIdAndDeletedFalse(documentId)
                .map(Document::getRefNo)
                .orElseThrow(() -> new IllegalStateException("Document not found: " + documentId));
    }

    private Map<Long, Boolean> snapshotRolePermissions(String roleName, AppPermission... permissions) {
        Map<String, AppPermission> required = new HashMap<>();
        for (AppPermission permission : permissions) {
            required.put(permission.name(), permission);
        }

        return rolePermissionRepository.findByRole_RoleNameIgnoreCaseOrderByPermissionNameAsc(roleName)
                .stream()
                .filter(entry -> required.containsKey(entry.getPermissionName()))
                .collect(HashMap::new, (map, entry) -> map.put(entry.getId(), Boolean.TRUE.equals(entry.getEnabled())), HashMap::putAll);
    }

    private void setRolePermission(String roleName, AppPermission permission, boolean enabled) {
        RolePermission rolePermission = rolePermissionRepository.findByRole_RoleNameIgnoreCaseOrderByPermissionNameAsc(roleName)
                .stream()
                .filter(entry -> permission.name().equalsIgnoreCase(entry.getPermissionName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Permission not found for role " + roleName + ": " + permission.name()));

        rolePermission.setEnabled(enabled);
        rolePermissionRepository.saveAndFlush(rolePermission);
    }

    private void restoreRolePermissions(Map<Long, Boolean> originals) {
        originals.forEach((id, enabled) -> {
            RolePermission rolePermission = rolePermissionRepository.findById(id)
                    .orElseThrow(() -> new IllegalStateException("Role permission not found: " + id));
            rolePermission.setEnabled(enabled);
            rolePermissionRepository.save(rolePermission);
        });
        rolePermissionRepository.flush();
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

    private void ensureApproveRejectButtonsEnabled() {
        DcAutoForwardConfig config = dcAutoForwardConfigRepository.findById(1L).orElseGet(DcAutoForwardConfig::new);
        config.setId(1L);
        config.setApproveRejectButtonsEnabled(true);
        config.setForwardReturnAllowedStatuses("PENDING,IN_PROGRESS,RETURNED");
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
}
