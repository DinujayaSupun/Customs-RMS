package lk.customs.rms.service;

import lk.customs.rms.dto.AuditLogFilterOptionsResponse;
import lk.customs.rms.dto.AuditLogResponse;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface AuditLogService {

    void logEvent(String entityType,
                  Long entityId,
                  String actionType,
                  Long userId,
                  String message,
                  String detailsJson);

    void logEventWithDetails(String entityType,
                             Long entityId,
                             String actionType,
                             Long userId,
                             String message,
                             Map<String, Object> details);

    void logDocumentCreate(Long documentId, Long userId, String message);

    void logDocumentUpdate(Long documentId, Long userId, String message);

    void logDocumentDelete(Long documentId, Long userId, String message);

    void logMovement(Long documentId, Long userId, String actionType, String message);

    void logAttachment(Long documentId, Long attachmentId, Long userId, String actionType, String message);

    // ✅ NEW: remark logging (stored as DOCUMENT entity_type so it appears in document history)
    void logRemark(Long documentId, Long userId, String actionType, String message, Long remarkId);

    // --- Query methods ---

    Page<AuditLogResponse> searchLogs(LocalDate fromDate, LocalDate toDate,
                                      String actionType, Long performedByUserId,
                                      String document, int page, int size);

    AuditLogFilterOptionsResponse getFilterOptions();

    byte[] exportCsvBytes(LocalDate fromDate, LocalDate toDate,
                          String actionType, Long performedByUserId,
                          String document);

    List<AuditLogResponse> getHistoryForDocument(Long documentId);
}
