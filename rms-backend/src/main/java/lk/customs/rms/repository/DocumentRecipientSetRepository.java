package lk.customs.rms.repository;

import lk.customs.rms.entity.DocumentRecipientSet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DocumentRecipientSetRepository extends JpaRepository<DocumentRecipientSet, Long> {
    Optional<DocumentRecipientSet> findFirstByDocumentIdAndActiveTrueOrderByCreatedAtDescIdDesc(Long documentId);

    Optional<DocumentRecipientSet> findFirstByDocumentIdAndMovementIdOrderByCreatedAtDescIdDesc(Long documentId, Long movementId);

    List<DocumentRecipientSet> findByDocumentIdOrderByCreatedAtDescIdDesc(Long documentId);

    @Modifying
    @Query("UPDATE DocumentRecipientSet s SET s.active = false WHERE s.documentId = :documentId AND s.active = true")
    void deactivateAllForDocument(@Param("documentId") Long documentId);
}
