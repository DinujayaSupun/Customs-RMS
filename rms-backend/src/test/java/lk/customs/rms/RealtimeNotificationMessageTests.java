package lk.customs.rms;

import lk.customs.rms.dto.RealtimeNotificationMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RealtimeNotificationMessageTests {

    @Test
    void documentForwardedMessageUsesDocumentForwardedTypeAndAssignmentText() {
        RealtimeNotificationMessage message = RealtimeNotificationMessage.documentForwarded(
                12L,
                "REF-12",
                "Import License Review",
                5L,
                "Sender User"
        );

        assertThat(message.type()).isEqualTo("DOCUMENT_FORWARDED");
        assertThat(message.message()).isEqualTo("New document assigned: Import License Review");
        assertThat(message.documentId()).isEqualTo(12L);
        assertThat(message.documentRefNo()).isEqualTo("REF-12");
        assertThat(message.documentTitle()).isEqualTo("Import License Review");
        assertThat(message.fromUserId()).isEqualTo(5L);
        assertThat(message.fromUserName()).isEqualTo("Sender User");
        assertThat(message.createdAt()).isNotNull();
    }

    @Test
    void documentReturnedMessageUsesDocumentReturnedTypeAndReturnedText() {
        RealtimeNotificationMessage message = RealtimeNotificationMessage.documentReturned(
                15L,
                "REF-15",
                "Weighted Composite Complexity Measure",
                8L,
                "Returning User"
        );

        assertThat(message.type()).isEqualTo("DOCUMENT_RETURNED");
        assertThat(message.message()).isEqualTo("Document returned: Weighted Composite Complexity Measure");
        assertThat(message.documentId()).isEqualTo(15L);
        assertThat(message.documentRefNo()).isEqualTo("REF-15");
        assertThat(message.documentTitle()).isEqualTo("Weighted Composite Complexity Measure");
        assertThat(message.fromUserId()).isEqualTo(8L);
        assertThat(message.fromUserName()).isEqualTo("Returning User");
        assertThat(message.createdAt()).isNotNull();
    }

    @Test
    void documentCopiedMessageUsesDocumentCopiedTypeAndCopiedText() {
        RealtimeNotificationMessage message = RealtimeNotificationMessage.documentCopied(
                12L,
                "REF-12",
                "Import License Review",
                5L,
                "Sender User"
        );

        assertThat(message.type()).isEqualTo("DOCUMENT_COPIED");
        assertThat(message.message()).isEqualTo("You were copied on a document: Import License Review");
        assertThat(message.documentId()).isEqualTo(12L);
        assertThat(message.fromUserId()).isEqualTo(5L);
        assertThat(message.fromUserName()).isEqualTo("Sender User");
    }

    @Test
    void documentNotificationFallsBackToReferenceWhenTitleIsBlank() {
        RealtimeNotificationMessage message = RealtimeNotificationMessage.documentForwarded(
                16L,
                "REF-16",
                " ",
                8L,
                "Sender User"
        );

        assertThat(message.message()).isEqualTo("New document assigned: REF-16");
    }
}
