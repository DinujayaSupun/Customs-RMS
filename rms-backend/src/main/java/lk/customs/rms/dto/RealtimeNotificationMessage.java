package lk.customs.rms.dto;

import java.time.LocalDateTime;

public record RealtimeNotificationMessage(
        String type,
        String message,
        Long documentId,
        String documentRefNo,
        Long fromUserId,
        String fromUserName,
        LocalDateTime createdAt
) {
    public static RealtimeNotificationMessage documentForwarded(
            Long documentId,
            String documentRefNo,
            Long fromUserId,
            String fromUserName
    ) {
        String refText = (documentRefNo == null || documentRefNo.isBlank())
                ? "Document #" + documentId
                : documentRefNo;

        return new RealtimeNotificationMessage(
                "DOCUMENT_FORWARDED",
                "New document assigned: " + refText,
                documentId,
                documentRefNo,
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
                                LocalDateTime.now()
                );
        }
}
