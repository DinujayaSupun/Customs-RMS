package lk.customs.rms.repository;

import lk.customs.rms.entity.DocumentMovement;
import lk.customs.rms.enums.MovementActionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DocumentMovementRepository extends JpaRepository<DocumentMovement, Long> {
    List<DocumentMovement> findByDocumentIdOrderByActionAtAsc(Long documentId);

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
