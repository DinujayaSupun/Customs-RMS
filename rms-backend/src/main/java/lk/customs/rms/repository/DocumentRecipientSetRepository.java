package lk.customs.rms.repository;

import lk.customs.rms.entity.DocumentRecipientSet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentRecipientSetRepository extends JpaRepository<DocumentRecipientSet, Long> {
    Optional<DocumentRecipientSet> findFirstByDocumentIdAndActiveTrueOrderByCreatedAtDescIdDesc(Long documentId);

    Optional<DocumentRecipientSet> findFirstByDocumentIdAndMovementIdOrderByCreatedAtDescIdDesc(Long documentId, Long movementId);

    List<DocumentRecipientSet> findByDocumentIdOrderByCreatedAtDescIdDesc(Long documentId);

    List<DocumentRecipientSet> findByDocumentIdAndActiveTrue(Long documentId);
}
