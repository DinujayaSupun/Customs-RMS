package lk.customs.rms.service;

import lk.customs.rms.dto.RealtimeNotificationMessage;
import lk.customs.rms.websocket.NotificationWebSocketHandler;
import org.springframework.stereotype.Service;

@Service
public class RealtimeNotificationService {

    private final NotificationWebSocketHandler notificationWebSocketHandler;

    public RealtimeNotificationService(NotificationWebSocketHandler notificationWebSocketHandler) {
        this.notificationWebSocketHandler = notificationWebSocketHandler;
    }

    public void notifyDocumentForwarded(Long recipientUserId,
                                        Long documentId,
                                        String documentRefNo,
                                        String documentTitle,
                                        Long fromUserId,
                                        String fromUserName) {
        RealtimeNotificationMessage message = RealtimeNotificationMessage.documentForwarded(
                documentId,
                documentRefNo,
                documentTitle,
                fromUserId,
                fromUserName
        );
        notificationWebSocketHandler.sendToUser(recipientUserId, message);
    }

    public void notifyDocumentCopied(Long recipientUserId,
                                     Long documentId,
                                     String documentRefNo,
                                     String documentTitle,
                                     Long fromUserId,
                                     String fromUserName) {
        RealtimeNotificationMessage message = RealtimeNotificationMessage.documentCopied(
                documentId,
                documentRefNo,
                documentTitle,
                fromUserId,
                fromUserName
        );
        notificationWebSocketHandler.sendToUser(recipientUserId, message);
    }

    public void notifyDocumentReturned(Long recipientUserId,
                                       Long documentId,
                                       String documentRefNo,
                                       String documentTitle,
                                       Long fromUserId,
                                       String fromUserName) {
        RealtimeNotificationMessage message = RealtimeNotificationMessage.documentReturned(
                documentId,
                documentRefNo,
                documentTitle,
                fromUserId,
                fromUserName
        );
        notificationWebSocketHandler.sendToUser(recipientUserId, message);
    }

    public void notifyDocumentUndoSend(Long recipientUserId,
                                       Long documentId,
                                       String documentRefNo,
                                       String documentTitle,
                                       Long fromUserId,
                                       String fromUserName) {
        RealtimeNotificationMessage message = RealtimeNotificationMessage.documentUndoSend(
                documentId,
                documentRefNo,
                documentTitle,
                fromUserId,
                fromUserName
        );
        notificationWebSocketHandler.sendToUser(recipientUserId, message);
    }

    public void notifyDocumentUndoReturnedToSender(Long recipientUserId,
                                                   Long documentId,
                                                   String documentRefNo,
                                                   String documentTitle,
                                                   Long fromUserId,
                                                   String fromUserName) {
        RealtimeNotificationMessage message = RealtimeNotificationMessage.documentUndoReturnedToSender(
                documentId,
                documentRefNo,
                documentTitle,
                fromUserId,
                fromUserName
        );
        notificationWebSocketHandler.sendToUser(recipientUserId, message);
    }

    public void notifyPermissionsUpdated() {
        notificationWebSocketHandler.sendToAll(RealtimeNotificationMessage.permissionsUpdated());
    }
}
