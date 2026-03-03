package lk.customs.rms.service;

public interface AuditLogService {

    void logDocumentCreate(Long documentId, Long userId, String message);

    void logDocumentUpdate(Long documentId, Long userId, String message);

    void logDocumentDelete(Long documentId, Long userId, String message);

    void logMovement(Long documentId, Long userId, String actionType, String message);

    void logAttachment(Long documentId, Long attachmentId, Long userId, String actionType, String message);

    // ✅ NEW: remark logging (stored as DOCUMENT entity_type so it appears in document history)
    void logRemark(Long documentId, Long userId, String actionType, String message, Long remarkId);
}
