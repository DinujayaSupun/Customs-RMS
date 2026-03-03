package lk.customs.rms.repository;

import lk.customs.rms.entity.DocumentRemark;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentRemarkRepository extends JpaRepository<DocumentRemark, Long> {
    List<DocumentRemark> findByDocumentIdOrderByRemarkedAtAsc(Long documentId);
}
