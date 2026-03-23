package lk.customs.rms.repository;

import lk.customs.rms.entity.DocumentUserView;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DocumentUserViewRepository extends JpaRepository<DocumentUserView, Long> {

    Optional<DocumentUserView> findByDocumentIdAndUserId(Long documentId, Long userId);

    void deleteByDocumentIdAndUserId(Long documentId, Long userId);
}
