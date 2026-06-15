package lk.customs.rms.service.impl;

import lk.customs.rms.entity.AuditLog;
import lk.customs.rms.repository.AuditLogRepository;
import lk.customs.rms.service.AuditLogService;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditLogServiceImpl(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void logEvent(String entityType,
                         Long entityId,
                         String actionType,
                         Long userId,
                         String message,
                         String detailsJson) {
        save(entityType, entityId, actionType, userId, message, detailsJson);
    }

    @Override
    public void logEventWithDetails(String entityType,
                                    Long entityId,
                                    String actionType,
                                    Long userId,
                                    String message,
                                    Map<String, Object> details) {
        save(entityType, entityId, actionType, userId, message, toDetailsJson(details));
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
        // Store documentId in details because the primary audit entity is the attachment row.
        String details = toDetailsJson(Map.of(
                "documentId", documentId,
                "attachmentId", attachmentId
        ));
        save("ATTACHMENT", attachmentId, actionType, userId, message, details);
    }

    @Override
    public void logRemark(Long documentId, Long userId, String actionType, String message, Long remarkId) {
        // ✅ Log as DOCUMENT so it is included when querying document history
        String details = remarkId == null ? null : toDetailsJson(Map.of(
                "documentId", documentId,
                "remarkId", remarkId
        ));
        save("DOCUMENT", documentId, actionType, userId, message, details);
    }

    private String toDetailsJson(Map<String, Object> details) {
        if (details == null || details.isEmpty()) return null;

        try {
            return objectMapper.writeValueAsString(details);
        } catch (JacksonException ex) {
            throw new IllegalArgumentException("Could not serialize audit details.", ex);
        }
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
