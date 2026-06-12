package lk.customs.rms;

import lk.customs.rms.dto.DocumentResponse;
import lk.customs.rms.entity.Document;
import lk.customs.rms.enums.Priority;
import lk.customs.rms.enums.Status;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentResponseMappingTests {

    @Test
    void mapsCoreDocumentFieldsAndNames() {
        Document document = document();

        DocumentResponse response = DocumentResponse.from(DocumentResponse.mapping(document)
                .createdByName("Creator Name")
                .ownerName("Owner Name")
                .mainAttachmentType("PDF")
                .viewedByMe(true)
                .build());

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getRefNo()).isEqualTo("REF-10");
        assertThat(response.getTitle()).isEqualTo("Import Permit");
        assertThat(response.getCompanyName()).isEqualTo("Acme Imports");
        assertThat(response.getVisibility()).isEqualTo("PRIVATE");
        assertThat(response.getPriority()).isEqualTo(Priority.HIGH);
        assertThat(response.getStatus()).isEqualTo(Status.IN_PROGRESS);
        assertThat(response.getCreatedByUserId()).isEqualTo(20L);
        assertThat(response.getCreatedByName()).isEqualTo("Creator Name");
        assertThat(response.getCurrentOwnerUserId()).isEqualTo(30L);
        assertThat(response.getCurrentOwnerName()).isEqualTo("Owner Name");
        assertThat(response.getMainAttachmentType()).isEqualTo("PDF");
        assertThat(response.getViewedByMe()).isTrue();
    }

    @Test
    void mapsInboxAndLatestRemarkFieldsByName() {
        Document document = document();
        LocalDateTime inboxReceivedAt = LocalDateTime.of(2026, 6, 12, 9, 10);
        LocalDateTime latestRemarkAt = LocalDateTime.of(2026, 6, 12, 9, 20);

        DocumentResponse response = DocumentResponse.from(DocumentResponse.mapping(document)
                .createdByName("Creator Name")
                .ownerName("Owner Name")
                .inboxReceivedAt(inboxReceivedAt)
                .inboxSenderUserId(40L)
                .inboxSenderName("Sender Name")
                .inboxSenderRole("SC")
                .latestRemarkPreview("Preview minute")
                .latestRemarkByUserId(50L)
                .latestRemarkByName("Minute Author")
                .latestRemarkByRole("DC")
                .latestRemarkTextPreview("Minute text preview")
                .latestRemarkText("Full minute text")
                .latestRemarkAt(latestRemarkAt)
                .build());

        assertThat(response.getInboxReceivedAt()).isEqualTo(inboxReceivedAt);
        assertThat(response.getInboxSenderUserId()).isEqualTo(40L);
        assertThat(response.getInboxSenderName()).isEqualTo("Sender Name");
        assertThat(response.getInboxSenderRole()).isEqualTo("SC");
        assertThat(response.getLatestRemarkPreview()).isEqualTo("Preview minute");
        assertThat(response.getLatestRemarkByUserId()).isEqualTo(50L);
        assertThat(response.getLatestRemarkByName()).isEqualTo("Minute Author");
        assertThat(response.getLatestRemarkByRole()).isEqualTo("DC");
        assertThat(response.getLatestRemarkTextPreview()).isEqualTo("Minute text preview");
        assertThat(response.getLatestRemarkText()).isEqualTo("Full minute text");
        assertThat(response.getLatestRemarkAt()).isEqualTo(latestRemarkAt);
    }

    @Test
    void mapsUndoSendFieldsByName() {
        Document document = document();
        LocalDateTime expiresAt = LocalDateTime.of(2026, 6, 12, 10, 0);

        DocumentResponse response = DocumentResponse.from(DocumentResponse.mapping(document)
                .createdByName("Creator Name")
                .ownerName("Owner Name")
                .canUndoSend(true)
                .undoSendStatus("AVAILABLE")
                .undoSendExpiresAt(expiresAt)
                .undoSendReceiverOpened(false)
                .undoSendActionType("FORWARD")
                .undoSendRequiresReason(true)
                .undoSendShowExpiredInfo(false)
                .undoSendByUserId(60L)
                .undoSendByName("Undo Actor")
                .undoSendByRole("DDC")
                .undoSendFromUserId(70L)
                .undoSendFromName("Original Sender")
                .undoSendFromRole("PMA")
                .build());

        assertThat(response.getCanUndoSend()).isTrue();
        assertThat(response.getUndoSendStatus()).isEqualTo("AVAILABLE");
        assertThat(response.getUndoSendExpiresAt()).isEqualTo(expiresAt);
        assertThat(response.getUndoSendReceiverOpened()).isFalse();
        assertThat(response.getUndoSendActionType()).isEqualTo("FORWARD");
        assertThat(response.getUndoSendRequiresReason()).isTrue();
        assertThat(response.getUndoSendShowExpiredInfo()).isFalse();
        assertThat(response.getUndoSendByUserId()).isEqualTo(60L);
        assertThat(response.getUndoSendByName()).isEqualTo("Undo Actor");
        assertThat(response.getUndoSendByRole()).isEqualTo("DDC");
        assertThat(response.getUndoSendFromUserId()).isEqualTo(70L);
        assertThat(response.getUndoSendFromName()).isEqualTo("Original Sender");
        assertThat(response.getUndoSendFromRole()).isEqualTo("PMA");
    }

    private Document document() {
        Document document = new Document();
        document.setId(10L);
        document.setRefNo("REF-10");
        document.setTitle("Import Permit");
        document.setReceivedDate(LocalDate.of(2026, 6, 11));
        document.setCompanyName("Acme Imports");
        document.setVisibility("PRIVATE");
        document.setPriority(Priority.HIGH);
        document.setStatus(Status.IN_PROGRESS);
        document.setCreatedByUserId(20L);
        document.setCurrentOwnerUserId(30L);
        document.setCreatedAt(LocalDateTime.of(2026, 6, 11, 8, 30));
        document.setUpdatedAt(LocalDateTime.of(2026, 6, 11, 8, 45));
        document.setCompletedAt(LocalDateTime.of(2026, 6, 12, 8, 0));
        document.setIssuedAt(LocalDateTime.of(2026, 6, 12, 8, 30));
        return document;
    }
}
