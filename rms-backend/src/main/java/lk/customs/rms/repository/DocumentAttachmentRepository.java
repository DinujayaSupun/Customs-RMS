package lk.customs.rms.repository;

import lk.customs.rms.entity.DocumentAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface DocumentAttachmentRepository extends JpaRepository<DocumentAttachment, Long> {

    List<DocumentAttachment> findByDocumentIdAndDeletedFalseOrderByVersionNoDesc(Long documentId);

    Optional<DocumentAttachment> findByIdAndDeletedFalse(Long id);

    @Query("select coalesce(max(a.versionNo), 0) from DocumentAttachment a where a.documentId = ?1")
    Integer findMaxVersionNo(Long documentId);

    List<DocumentAttachment> findByDocumentIdAndDeletedFalseOrderByVersionNoAsc(Long documentId);

    List<DocumentAttachment> findByDocumentIdInAndDeletedFalseAndIsLatestTrue(List<Long> documentIds);

    Optional<DocumentAttachment> findFirstByDocumentIdAndDeletedFalseAndIsLatestTrue(Long documentId);
}
