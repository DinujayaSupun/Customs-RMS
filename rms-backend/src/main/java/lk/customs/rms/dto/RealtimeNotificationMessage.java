package lk.customs.rms.dto;

import java.time.LocalDateTime;

public record RealtimeNotificationMessage(
        String type,
        String message,
        Long documentId,
        String documentRefNo,
        String documentTitle,
        Long fromUserId,
        String fromUserName,
        LocalDateTime createdAt
) {
    public static RealtimeNotificationMessage documentForwarded(
            Long documentId,
            String documentRefNo,
            String documentTitle,
            Long fromUserId,
            String fromUserName
    ) {
        String documentText = (documentTitle == null || documentTitle.isBlank())
                ? null
                : documentTitle;
        if (documentText == null) {
            documentText = (documentRefNo == null || documentRefNo.isBlank())
                ? "Document #" + documentId
                : documentRefNo;
        }

        return new RealtimeNotificationMessage(
                "DOCUMENT_FORWARDED",
                "New document assigned: " + documentText,
                documentId,
                documentRefNo,
                documentTitle,
                fromUserId,
                fromUserName,
                LocalDateTime.now()
        );
    }

    public static RealtimeNotificationMessage documentReturned(
            Long documentId,
            String documentRefNo,
            String documentTitle,
            Long fromUserId,
            String fromUserName
    ) {
        String documentText = (documentTitle == null || documentTitle.isBlank())
                ? null
                : documentTitle;
        if (documentText == null) {
            documentText = (documentRefNo == null || documentRefNo.isBlank())
                ? "Document #" + documentId
                : documentRefNo;
        }

        return new RealtimeNotificationMessage(
                "DOCUMENT_RETURNED",
                "Document returned: " + documentText,
                documentId,
                documentRefNo,
                documentTitle,
                fromUserId,
                fromUserName,
                LocalDateTime.now()
        );
    }

    public static RealtimeNotificationMessage documentUndoSend(
            Long documentId,
            String documentRefNo,
            String documentTitle,
            Long fromUserId,
            String fromUserName
    ) {
        String documentText = (documentTitle == null || documentTitle.isBlank())
                ? null
                : documentTitle;
        if (documentText == null) {
            documentText = (documentRefNo == null || documentRefNo.isBlank())
                    ? "Document #" + documentId
                    : documentRefNo;
        }

        return new RealtimeNotificationMessage(
                "DOCUMENT_UNDO_SEND",
                "Document send was undone: " + documentText,
                documentId,
                documentRefNo,
                documentTitle,
                fromUserId,
                fromUserName,
                LocalDateTime.now()
        );
    }

    public static RealtimeNotificationMessage documentUndoReturnedToSender(
            Long documentId,
            String documentRefNo,
            String documentTitle,
            Long fromUserId,
            String fromUserName
    ) {
        String documentText = (documentTitle == null || documentTitle.isBlank())
                ? null
                : documentTitle;
        if (documentText == null) {
            documentText = (documentRefNo == null || documentRefNo.isBlank())
                    ? "Document #" + documentId
                    : documentRefNo;
        }

        return new RealtimeNotificationMessage(
                "DOCUMENT_UNDO_RETURNED",
                "Document returned to you after undo: " + documentText,
                documentId,
                documentRefNo,
                documentTitle,
                fromUserId,
                fromUserName,
                LocalDateTime.now()
        );
    }

        public static RealtimeNotificationMessage permissionsUpdated() {
                return new RealtimeNotificationMessage(
                                "PERMISSIONS_UPDATED",
                                "Permissions were updated by admin. Refreshing your access.",
                                null,
                                null,
                                null,
                                null,
                                null,
                                LocalDateTime.now()
                );
        }
}
