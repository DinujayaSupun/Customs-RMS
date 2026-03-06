package lk.customs.rms.repository;

import lk.customs.rms.entity.Document;
import lk.customs.rms.enums.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    @Query("select case when count(d) > 0 then true else false end from Document d where d.refNo = :refNo and d.deleted = false")
    boolean existsByRefNoAndDeletedFalse(String refNo);

    @Query("""
           select case when count(d) > 0 then true else false end
             from Document d
            where d.deleted = false
              and lower(d.refNo) = lower(:refNo)
              and d.id <> :id
           """)
    boolean existsByRefNoAndDeletedFalseAndIdNot(@Param("refNo") String refNo, @Param("id") Long id);

    @Query("select d from Document d where d.deleted = false")
    Page<Document> findAllNotDeleted(Pageable pageable);

    @Query("""
           select d from Document d
           where d.deleted = false
             and (
                 lower(d.refNo) like lower(concat('%', :search, '%'))
              or lower(d.title) like lower(concat('%', :search, '%'))
              or lower(d.companyName) like lower(concat('%', :search, '%'))
             )
           """)
    Page<Document> searchNotDeleted(String search, Pageable pageable);

    Optional<Document> findByIdAndDeletedFalse(Long id);

    @Modifying
    @Query("""
           update Document d
              set d.currentOwnerUserId = :newOwnerId
            where d.deleted = false
              and d.currentOwnerUserId = :oldOwnerId
              and d.status <> :excludedStatus
           """)
    int transferOwnershipForActiveDocuments(@Param("oldOwnerId") Long oldOwnerId,
                                            @Param("newOwnerId") Long newOwnerId,
                                            @Param("excludedStatus") Status excludedStatus);
}
