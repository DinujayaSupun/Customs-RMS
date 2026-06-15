package lk.customs.rms.repository;

import lk.customs.rms.entity.DocumentMovement;
import lk.customs.rms.enums.MovementActionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DocumentMovementRepository extends JpaRepository<DocumentMovement, Long> {
    List<DocumentMovement> findByDocumentIdOrderByActionAtAsc(Long documentId);
    List<DocumentMovement> findByActionTypeAndActionByUserIdOrderByActionAtDescIdDesc(MovementActionType actionType, Long actionByUserId);
    List<DocumentMovement> findByActionTypeInAndActionByUserIdOrderByActionAtDescIdDesc(Collection<MovementActionType> actionTypes, Long actionByUserId);
    List<DocumentMovement> findByActionTypeAndFromUserIdOrderByActionAtDescIdDesc(MovementActionType actionType, Long fromUserId);
    @Query("""
           select m
           from DocumentMovement m
           join Document d on d.id = m.documentId
           where d.deleted = false
             and (
               (m.actionType in :sentActions and m.actionByUserId = :actorUserId)
               or (m.actionType = :undoAction and m.fromUserId = :actorUserId)
             )
             and (:search is null or :search = ''
               or lower(d.refNo) like lower(concat('%', :search, '%'))
               or lower(d.title) like lower(concat('%', :search, '%'))
               or lower(d.companyName) like lower(concat('%', :search, '%')))
             and (:status is null or d.status = :status)
             and (:priority is null or d.priority = :priority)
           """)
    Page<DocumentMovement> findSentPageForActor(@Param("actorUserId") Long actorUserId,
                                                @Param("sentActions") Collection<MovementActionType> sentActions,
                                                @Param("undoAction") MovementActionType undoAction,
                                                @Param("search") String search,
                                                @Param("status") lk.customs.rms.enums.Status status,
                                                @Param("priority") lk.customs.rms.enums.Priority priority,
                                                Pageable pageable);
    Optional<DocumentMovement> findFirstByDocumentIdOrderByActionAtDescIdDesc(Long documentId);
    List<DocumentMovement> findByDocumentIdInAndToUserIdAndActionTypeInOrderByDocumentIdAscActionAtAsc(
            List<Long> documentIds,
            Long toUserId,
            Collection<MovementActionType> actionTypes
    );

    Optional<DocumentMovement> findFirstByDocumentIdAndToUserIdAndActionTypeInOrderByActionAtDescIdDesc(
            Long documentId,
            Long toUserId,
            Collection<MovementActionType> actionTypes
    );
    List<DocumentMovement> findByDocumentIdInAndActionTypeOrderByDocumentIdAscActionAtAsc(
            List<Long> documentIds,
            MovementActionType actionType
    );

    boolean existsByDocumentIdAndActionAtAfter(Long documentId, java.time.LocalDateTime actionAt);

        @Query("""
                     select m
                     from DocumentMovement m
                     where m.documentId in :documentIds
                         and m.toUserId = :actorUserId
                         and m.actionType in :inboundActions
                         and m.actionAt = (
                                 select max(m2.actionAt)
                                 from DocumentMovement m2
                                 where m2.documentId = m.documentId
                                     and m2.toUserId = :actorUserId
                                     and m2.actionType in :inboundActions
                         )
                     """)
        List<DocumentMovement> findLatestInboundByActorAndDocumentIds(@Param("actorUserId") Long actorUserId,
                                                                                                                                    @Param("documentIds") List<Long> documentIds,
                                                                                                                                    @Param("inboundActions") Collection<MovementActionType> inboundActions);

        @Query("""
                     select case when count(m) > 0 then true else false end
                     from DocumentMovement m
                     where m.documentId = :documentId
                         and m.actionType = :forwardAction
                         and upper(coalesce(m.forwardVisibility, '')) = 'PRIVATE'
                         and (m.fromUserId = :userId or m.toUserId = :userId)
                     """)
        boolean existsPrivateForwardTrailForUser(@Param("documentId") Long documentId,
                                                                                         @Param("userId") Long userId,
                                                                                         @Param("forwardAction") MovementActionType forwardAction);
}
