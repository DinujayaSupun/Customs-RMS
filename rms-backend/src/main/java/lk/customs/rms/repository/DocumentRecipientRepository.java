package lk.customs.rms.repository;

import lk.customs.rms.entity.DocumentRecipient;
import lk.customs.rms.enums.RecipientType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface DocumentRecipientRepository extends JpaRepository<DocumentRecipient, Long> {
    List<DocumentRecipient> findByRecipientSetIdOrderByRecipientTypeAscUserIdAsc(Long recipientSetId);

    List<DocumentRecipient> findByRecipientSetIdInOrderByRecipientSetIdAscRecipientTypeAscUserIdAsc(Collection<Long> recipientSetIds);

    @Query("""
            select r from DocumentRecipient r
            join DocumentRecipientSet s on s.id = r.recipientSetId
            where s.active = true
              and r.documentId = :documentId
              and r.userId = :userId
              and r.removedAt is null
            order by case r.recipientType
                when lk.customs.rms.enums.RecipientType.TO then 0
                when lk.customs.rms.enums.RecipientType.CC then 1
                else 2
            end
            """)
    List<DocumentRecipient> findActiveForDocumentAndUser(@Param("documentId") Long documentId,
                                                          @Param("userId") Long userId);

    @Query("""
            select r from DocumentRecipient r
            join DocumentRecipientSet s on s.id = r.recipientSetId
            where s.active = true
              and r.documentId in :documentIds
              and r.removedAt is null
            order by r.documentId asc, r.recipientType asc, r.userId asc
            """)
    List<DocumentRecipient> findActiveForDocuments(@Param("documentIds") Collection<Long> documentIds);
}
