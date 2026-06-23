package lk.customs.rms;

import lk.customs.rms.dto.RealtimeNotificationMessage;
import lk.customs.rms.websocket.NotificationWebSocketHandler;
import lk.customs.rms.service.RealtimeNotificationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RealtimeNotificationServiceTests {

    @Test
    void notifyDocumentForwardedSendsForwardedMessageToRecipient() {
        NotificationWebSocketHandler handler = mock(NotificationWebSocketHandler.class);
        RealtimeNotificationService service = new RealtimeNotificationService(handler);

        service.notifyDocumentForwarded(22L, 10L, "REF-10", "Import License Review", 5L, "Forwarder");

        ArgumentCaptor<RealtimeNotificationMessage> messageCaptor = ArgumentCaptor.forClass(RealtimeNotificationMessage.class);
        verify(handler).sendToUser(org.mockito.ArgumentMatchers.eq(22L), messageCaptor.capture());
        assertThat(messageCaptor.getValue().type()).isEqualTo("DOCUMENT_FORWARDED");
        assertThat(messageCaptor.getValue().message()).isEqualTo("New document assigned: Import License Review");
        assertThat(messageCaptor.getValue().documentTitle()).isEqualTo("Import License Review");
    }

    @Test
    void notifyDocumentReturnedSendsReturnedMessageToRecipient() {
        NotificationWebSocketHandler handler = mock(NotificationWebSocketHandler.class);
        RealtimeNotificationService service = new RealtimeNotificationService(handler);

        service.notifyDocumentReturned(11L, 9L, "REF-9", "Weighted Composite Complexity Measure", 6L, "Returner");

        ArgumentCaptor<RealtimeNotificationMessage> messageCaptor = ArgumentCaptor.forClass(RealtimeNotificationMessage.class);
        verify(handler).sendToUser(org.mockito.ArgumentMatchers.eq(11L), messageCaptor.capture());
        assertThat(messageCaptor.getValue().type()).isEqualTo("DOCUMENT_RETURNED");
        assertThat(messageCaptor.getValue().message()).isEqualTo("Document returned: Weighted Composite Complexity Measure");
        assertThat(messageCaptor.getValue().documentTitle()).isEqualTo("Weighted Composite Complexity Measure");
    }
}
