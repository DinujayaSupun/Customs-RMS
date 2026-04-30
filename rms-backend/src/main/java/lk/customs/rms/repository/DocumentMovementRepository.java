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
