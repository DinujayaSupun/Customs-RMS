package lk.customs.rms.repository;

import lk.customs.rms.entity.DocumentUserView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DocumentUserViewRepository extends JpaRepository<DocumentUserView, Long> {

    Optional<DocumentUserView> findByDocumentIdAndUserId(Long documentId, Long userId);

    @Query("select v.documentId from DocumentUserView v where v.userId = :userId and v.documentId in :documentIds")
    List<Long> findViewedDocumentIdsByUser(@Param("userId") Long userId,
                                           @Param("documentIds") List<Long> documentIds);

    void deleteByDocumentIdAndUserId(Long documentId, Long userId);
}
