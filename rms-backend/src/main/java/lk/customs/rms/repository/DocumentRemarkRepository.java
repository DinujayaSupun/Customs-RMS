package lk.customs.rms.repository;

import lk.customs.rms.entity.DocumentRemark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DocumentRemarkRepository extends JpaRepository<DocumentRemark, Long> {
    List<DocumentRemark> findByDocumentIdOrderByRemarkedAtAsc(Long documentId);

    Optional<DocumentRemark> findFirstByDocumentIdOrderByRemarkedAtDesc(Long documentId);

    @Query("""
           select r
           from DocumentRemark r
           join fetch r.remarkedBy
           where r.documentId = :documentId
           order by r.remarkedAt desc
           limit 1
           """)
    Optional<DocumentRemark> findFirstByDocumentIdOrderByRemarkedAtDescWithUser(@Param("documentId") Long documentId);

    @Query("""
           select r
           from DocumentRemark r
           join fetch r.remarkedBy u
           join fetch u.role
           where r.documentId in :documentIds
             and r.remarkedAt = (
                 select max(r2.remarkedAt)
                 from DocumentRemark r2
                 where r2.documentId = r.documentId
             )
           """)
    List<DocumentRemark> findLatestByDocumentIdsWithUser(@Param("documentIds") List<Long> documentIds);

    @Query("""
           select r
           from DocumentRemark r
           where r.documentId in :documentIds
             and r.remarkedAt = (
                 select max(r2.remarkedAt)
                 from DocumentRemark r2
                 where r2.documentId = r.documentId
             )
           """)
    List<DocumentRemark> findLatestByDocumentIds(@Param("documentIds") List<Long> documentIds);
}
