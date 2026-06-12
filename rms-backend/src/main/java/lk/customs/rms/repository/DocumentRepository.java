package lk.customs.rms.repository;

import lk.customs.rms.entity.Document;
import lk.customs.rms.enums.MovementActionType;
import lk.customs.rms.enums.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
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
                    d.currentOwnerUserId = :userId
              or
                 (upper(coalesce(d.visibility, 'PUBLIC')) = 'PUBLIC' and :canViewPublic = true)
              or (
                    upper(coalesce(d.visibility, 'PUBLIC')) = 'PRIVATE'
                and (
                       (:canViewPrivate = true and (
                            d.currentOwnerUserId = :userId
                         or exists (
                             select m.id from DocumentMovement m
                             where m.documentId = d.id
                               and m.actionType = :forwardAction
                               and upper(coalesce(m.forwardVisibility, '')) = 'PRIVATE'
                               and (m.fromUserId = :userId or m.toUserId = :userId)
                         )
                       ))
                    or (:canViewOwnCreated = true and d.createdByUserId = :userId)
                )
              )
             )
           """)
    Page<Document> findAccessibleNotDeleted(@Param("userId") Long userId,
                                            @Param("canViewPublic") boolean canViewPublic,
                                            @Param("canViewPrivate") boolean canViewPrivate,
                                            @Param("canViewOwnCreated") boolean canViewOwnCreated,
                                            @Param("forwardAction") MovementActionType forwardAction,
                                            Pageable pageable);

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

    @Query("""
           select d from Document d
           where d.deleted = false
             and (
                 lower(d.refNo) like lower(concat('%', :search, '%'))
              or lower(d.title) like lower(concat('%', :search, '%'))
              or lower(d.companyName) like lower(concat('%', :search, '%'))
             )
             and (
            d.currentOwnerUserId = :userId
          or
                 (upper(coalesce(d.visibility, 'PUBLIC')) = 'PUBLIC' and :canViewPublic = true)
              or (
                    upper(coalesce(d.visibility, 'PUBLIC')) = 'PRIVATE'
                and (
                       (:canViewPrivate = true and (
                            d.currentOwnerUserId = :userId
                         or exists (
                             select m.id from DocumentMovement m
                             where m.documentId = d.id
                               and m.actionType = :forwardAction
                               and upper(coalesce(m.forwardVisibility, '')) = 'PRIVATE'
                               and (m.fromUserId = :userId or m.toUserId = :userId)
                         )
                       ))
                    or (:canViewOwnCreated = true and d.createdByUserId = :userId)
                )
              )
             )
           """)
    Page<Document> searchAccessibleNotDeleted(@Param("search") String search,
                                              @Param("userId") Long userId,
                                              @Param("canViewPublic") boolean canViewPublic,
                                              @Param("canViewPrivate") boolean canViewPrivate,
                                              @Param("canViewOwnCreated") boolean canViewOwnCreated,
                                              @Param("forwardAction") MovementActionType forwardAction,
                                              Pageable pageable);

    @Query("""
           select d from Document d
           where d.deleted = false
             and d.currentOwnerUserId in :dcUserIds
             and d.dcAssignedAt is not null
             and d.dcViewedAt is null
             and d.status not in (lk.customs.rms.enums.Status.ISSUED, lk.customs.rms.enums.Status.REJECTED, lk.customs.rms.enums.Status.APPROVED)
           """)
    List<Document> findPendingDcAutoForwardCandidates(@Param("dcUserIds") List<Long> dcUserIds);

    Optional<Document> findByIdAndDeletedFalse(Long id);

    @Query("""
           select d
           from Document d
           where d.deleted = false
             and d.currentOwnerUserId = :userId
             and d.status <> :excludedStatus
           """)
    Page<Document> findAssignedActiveByOwner(@Param("userId") Long userId,
                                             @Param("excludedStatus") Status excludedStatus,
                                             Pageable pageable);

    @Query("""
           select count(d)
           from Document d
           where d.deleted = false
             and d.currentOwnerUserId = :userId
             and d.status <> :excludedStatus
           """)
    long countAssignedActiveByOwner(@Param("userId") Long userId,
                                    @Param("excludedStatus") Status excludedStatus);

    @Query("""
           select count(d)
           from Document d
           where d.deleted = false
             and d.currentOwnerUserId = :userId
             and d.status <> :excludedStatus
             and exists (
                 select v.id
                 from DocumentUserView v
                 where v.documentId = d.id
                   and v.userId = :userId
             )
           """)
    long countOpenedAssignedActiveByOwner(@Param("userId") Long userId,
                                          @Param("excludedStatus") Status excludedStatus);

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
