package lk.customs.rms.repository;

import lk.customs.rms.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByEntityTypeAndEntityIdOrderByPerformedAtAsc(String entityType, Long entityId);

    // ✅ Full document history: document + movements + attachment logs (via details_json containing documentId)
    @Query(value = """
        SELECT * FROM audit_logs al
        WHERE (al.entity_type = 'DOCUMENT' AND al.entity_id = :documentId)
           OR (al.entity_type = 'MOVEMENT' AND al.entity_id = :documentId)
           OR (al.entity_type = 'ATTACHMENT' AND al.details_json LIKE CONCAT('%\\"documentId\\":', :documentId, '%'))
        ORDER BY al.performed_at ASC
        """, nativeQuery = true)
    List<AuditLog> findHistoryForDocument(@Param("documentId") Long documentId);
}
