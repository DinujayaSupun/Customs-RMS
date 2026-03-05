package lk.customs.rms.repository;

import lk.customs.rms.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
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

    @Query(value = """
        SELECT al.*
        FROM audit_logs al
        LEFT JOIN documents d ON (
             (al.entity_type IN ('DOCUMENT', 'MOVEMENT') AND al.entity_id = d.id)
             OR
             (al.entity_type = 'ATTACHMENT' AND EXISTS (
                 SELECT 1
                 FROM document_attachments da
                 WHERE da.id = al.entity_id AND da.document_id = d.id
             ))
        )
        WHERE (:fromAt IS NULL OR al.performed_at >= :fromAt)
          AND (:toAtExclusive IS NULL OR al.performed_at < :toAtExclusive)
          AND (:actionType IS NULL OR :actionType = '' OR UPPER(al.action_type) = UPPER(:actionType))
          AND (:performedByUserId IS NULL OR al.performed_by_user_id = :performedByUserId)
          AND (
                :documentFilter IS NULL OR :documentFilter = ''
                OR ((:documentId IS NOT NULL) AND (
                        (al.entity_type IN ('DOCUMENT', 'MOVEMENT') AND al.entity_id = :documentId)
                        OR (al.entity_type = 'ATTACHMENT' AND EXISTS (
                            SELECT 1
                            FROM document_attachments da2
                            WHERE da2.id = al.entity_id AND da2.document_id = :documentId
                        ))
                    ))
                OR (d.ref_no IS NOT NULL AND LOWER(d.ref_no) LIKE LOWER(CONCAT('%', :documentFilter, '%')))
          )
        ORDER BY al.performed_at DESC, al.id DESC
        """,
        countQuery = """
        SELECT COUNT(DISTINCT al.id)
        FROM audit_logs al
        LEFT JOIN documents d ON (
             (al.entity_type IN ('DOCUMENT', 'MOVEMENT') AND al.entity_id = d.id)
             OR
             (al.entity_type = 'ATTACHMENT' AND EXISTS (
                 SELECT 1
                 FROM document_attachments da
                 WHERE da.id = al.entity_id AND da.document_id = d.id
             ))
        )
        WHERE (:fromAt IS NULL OR al.performed_at >= :fromAt)
          AND (:toAtExclusive IS NULL OR al.performed_at < :toAtExclusive)
          AND (:actionType IS NULL OR :actionType = '' OR UPPER(al.action_type) = UPPER(:actionType))
          AND (:performedByUserId IS NULL OR al.performed_by_user_id = :performedByUserId)
          AND (
                :documentFilter IS NULL OR :documentFilter = ''
                OR ((:documentId IS NOT NULL) AND (
                        (al.entity_type IN ('DOCUMENT', 'MOVEMENT') AND al.entity_id = :documentId)
                        OR (al.entity_type = 'ATTACHMENT' AND EXISTS (
                            SELECT 1
                            FROM document_attachments da2
                            WHERE da2.id = al.entity_id AND da2.document_id = :documentId
                        ))
                    ))
                OR (d.ref_no IS NOT NULL AND LOWER(d.ref_no) LIKE LOWER(CONCAT('%', :documentFilter, '%')))
          )
        """,
        nativeQuery = true)
    Page<AuditLog> searchLogs(@Param("fromAt") LocalDateTime fromAt,
                              @Param("toAtExclusive") LocalDateTime toAtExclusive,
                              @Param("actionType") String actionType,
                              @Param("performedByUserId") Long performedByUserId,
                              @Param("documentFilter") String documentFilter,
                              @Param("documentId") Long documentId,
                              Pageable pageable);

    @Query(value = """
        SELECT al.*
        FROM audit_logs al
        LEFT JOIN documents d ON (
             (al.entity_type IN ('DOCUMENT', 'MOVEMENT') AND al.entity_id = d.id)
             OR
             (al.entity_type = 'ATTACHMENT' AND EXISTS (
                 SELECT 1
                 FROM document_attachments da
                 WHERE da.id = al.entity_id AND da.document_id = d.id
             ))
        )
        WHERE (:fromAt IS NULL OR al.performed_at >= :fromAt)
          AND (:toAtExclusive IS NULL OR al.performed_at < :toAtExclusive)
          AND (:actionType IS NULL OR :actionType = '' OR UPPER(al.action_type) = UPPER(:actionType))
          AND (:performedByUserId IS NULL OR al.performed_by_user_id = :performedByUserId)
          AND (
                :documentFilter IS NULL OR :documentFilter = ''
                OR ((:documentId IS NOT NULL) AND (
                        (al.entity_type IN ('DOCUMENT', 'MOVEMENT') AND al.entity_id = :documentId)
                        OR (al.entity_type = 'ATTACHMENT' AND EXISTS (
                            SELECT 1
                            FROM document_attachments da2
                            WHERE da2.id = al.entity_id AND da2.document_id = :documentId
                        ))
                    ))
                OR (d.ref_no IS NOT NULL AND LOWER(d.ref_no) LIKE LOWER(CONCAT('%', :documentFilter, '%')))
          )
        ORDER BY al.performed_at DESC, al.id DESC
        """, nativeQuery = true)
    List<AuditLog> exportLogs(@Param("fromAt") LocalDateTime fromAt,
                              @Param("toAtExclusive") LocalDateTime toAtExclusive,
                              @Param("actionType") String actionType,
                              @Param("performedByUserId") Long performedByUserId,
                              @Param("documentFilter") String documentFilter,
                              @Param("documentId") Long documentId);
}
