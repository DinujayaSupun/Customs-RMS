package lk.customs.rms.service.impl;

import lk.customs.rms.entity.AuditLog;
import lk.customs.rms.repository.AuditLogRepository;
import lk.customs.rms.service.AuditLogService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogServiceImpl(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    public void logDocumentCreate(Long documentId, Long userId, String message) {
        save("DOCUMENT", documentId, "CREATE", userId, message, null);
    }

    @Override
    public void logDocumentUpdate(Long documentId, Long userId, String message) {
        save("DOCUMENT", documentId, "UPDATE", userId, message, null);
    }

    @Override
    public void logDocumentDelete(Long documentId, Long userId, String message) {
        save("DOCUMENT", documentId, "DELETE", userId, message, null);
    }

    @Override
    public void logMovement(Long documentId, Long userId, String actionType, String message) {
        save("MOVEMENT", documentId, actionType, userId, message, null);
    }

    @Override
    public void logAttachment(Long documentId, Long attachmentId, Long userId, String actionType, String message) {
        String details = "{\"documentId\":" + documentId + ",\"attachmentId\":" + attachmentId + "}";
        save("ATTACHMENT", attachmentId, actionType, userId, message, details);
    }

    @Override
    public void logRemark(Long documentId, Long userId, String actionType, String message, Long remarkId) {
        // ✅ Log as DOCUMENT so it is included when querying document history
        String details = remarkId == null ? null : ("{\"documentId\":" + documentId + ",\"remarkId\":" + remarkId + "}");
        save("DOCUMENT", documentId, actionType, userId, message, details);
    }

    private void save(String entityType, Long entityId, String actionType, Long userId, String message, String detailsJson) {
        AuditLog log = new AuditLog();
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setActionType(actionType);
        log.setPerformedByUserId(userId);
        log.setPerformedAt(LocalDateTime.now());
        log.setMessage(message);
        log.setDetailsJson(detailsJson);
        auditLogRepository.save(log);
    }
}
