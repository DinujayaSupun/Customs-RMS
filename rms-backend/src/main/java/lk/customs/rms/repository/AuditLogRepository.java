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

    List<AuditLog> findByEntityTypeAndEntityIdInAndActionTypeOrderByPerformedAtAsc(String entityType, List<Long> entityIds, String actionType);

    @Query("""
        select distinct al.actionType
        from AuditLog al
        where al.actionType is not null
        order by al.actionType asc
        """)
    List<String> findDistinctActionTypes();

    @Query("""
        select distinct al.performedByUserId
        from AuditLog al
        where al.performedByUserId is not null
        order by al.performedByUserId asc
        """)
    List<Long> findDistinctPerformedByUserIds();

    List<AuditLog> findByEntityTypeAndEntityIdOrderByPerformedAtAsc(String entityType, Long entityId);

    // Full document history: the document's own logs, its movement logs, and attachment logs
    // (attachment logs carry the owning documentId inside details_json).
    // JPQL (not native SQL) so the query is portable across databases via the Hibernate dialect.
    @Query("""
        select al from AuditLog al
        where (al.entityType = 'DOCUMENT' and al.entityId = :documentId)
           or (al.entityType = 'MOVEMENT' and al.entityId = :documentId)
           or (al.entityType = 'ATTACHMENT'
               and al.detailsJson like concat('%"documentId":', cast(:documentId as String), '%'))
        order by al.performedAt asc
        """)
    List<AuditLog> findHistoryForDocument(@Param("documentId") Long documentId);

    // Audit-log search with optional date/action/performer/document filters.
    // The document filter matches either a numeric documentId (directly, or via an attachment that
    // belongs to that document) or any related document whose reference number contains the text.
    // Written as JPQL with correlated EXISTS subqueries so it stays database-portable.
    @Query(value = """
        select al from AuditLog al
        where (:fromAt is null or al.performedAt >= :fromAt)
          and (:toAtExclusive is null or al.performedAt < :toAtExclusive)
          and (:actionType is null or :actionType = '' or upper(al.actionType) = upper(:actionType))
          and (:performedByUserId is null or al.performedByUserId = :performedByUserId)
          and (
                :documentFilter is null or :documentFilter = ''
                or (:documentId is not null and (
                        (al.entityType in ('DOCUMENT', 'MOVEMENT') and al.entityId = :documentId)
                        or (al.entityType = 'ATTACHMENT' and exists (
                                select da.id from DocumentAttachment da
                                where da.id = al.entityId and da.documentId = :documentId))
                   ))
                or exists (
                        select d.id from Document d
                        where (
                                (al.entityType in ('DOCUMENT', 'MOVEMENT') and al.entityId = d.id)
                                or (al.entityType = 'ATTACHMENT' and exists (
                                        select da2.id from DocumentAttachment da2
                                        where da2.id = al.entityId and da2.documentId = d.id))
                              )
                          and d.refNo is not null
                          and lower(d.refNo) like lower(concat('%', :documentFilter, '%')))
          )
        order by al.performedAt desc, al.id desc
        """,
        countQuery = """
        select count(al) from AuditLog al
        where (:fromAt is null or al.performedAt >= :fromAt)
          and (:toAtExclusive is null or al.performedAt < :toAtExclusive)
          and (:actionType is null or :actionType = '' or upper(al.actionType) = upper(:actionType))
          and (:performedByUserId is null or al.performedByUserId = :performedByUserId)
          and (
                :documentFilter is null or :documentFilter = ''
                or (:documentId is not null and (
                        (al.entityType in ('DOCUMENT', 'MOVEMENT') and al.entityId = :documentId)
                        or (al.entityType = 'ATTACHMENT' and exists (
                                select da.id from DocumentAttachment da
                                where da.id = al.entityId and da.documentId = :documentId))
                   ))
                or exists (
                        select d.id from Document d
                        where (
                                (al.entityType in ('DOCUMENT', 'MOVEMENT') and al.entityId = d.id)
                                or (al.entityType = 'ATTACHMENT' and exists (
                                        select da2.id from DocumentAttachment da2
                                        where da2.id = al.entityId and da2.documentId = d.id))
                              )
                          and d.refNo is not null
                          and lower(d.refNo) like lower(concat('%', :documentFilter, '%')))
          )
        """)
    Page<AuditLog> searchLogs(@Param("fromAt") LocalDateTime fromAt,
                              @Param("toAtExclusive") LocalDateTime toAtExclusive,
                              @Param("actionType") String actionType,
                              @Param("performedByUserId") Long performedByUserId,
                              @Param("documentFilter") String documentFilter,
                              @Param("documentId") Long documentId,
                              Pageable pageable);
}
