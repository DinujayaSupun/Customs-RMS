package lk.customs.rms.service;

import lk.customs.rms.dto.RecipientSummaryResponse;
import lk.customs.rms.entity.Document;
import lk.customs.rms.entity.DocumentRecipient;
import lk.customs.rms.enums.RecipientSetReason;
import lk.customs.rms.enums.RecipientType;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface DocumentRecipientService {
    void createInitialSet(Document document, Long actorUserId, Long movementId);

    void createForwardSet(Document document, Long toUserId, List<Long> ccUserIds, List<Long> bccUserIds,
                          Long actorUserId, Long movementId);

    void createOwnerOnlySet(Document document, Long toUserId, Long actorUserId, Long movementId, RecipientSetReason reason);

    void updateCopiedRecipients(Document document, List<Long> ccUserIds, List<Long> bccUserIds, Long actorUserId);

    void restorePreviousSet(Document document, Long actorUserId, Long movementId, RecipientSetReason reason);

    void restorePreviousSetForTo(Document document, Long toUserId, Long actorUserId, Long movementId, RecipientSetReason reason);

    void preserveCopiedRecipientsWithNewTo(Document document, Long newToUserId, Long actorUserId, Long movementId,
                                           RecipientSetReason reason);

    Optional<RecipientType> activeRecipientType(Long documentId, Long userId);

    boolean isActiveRecipient(Long documentId, Long userId);

    boolean canViewAsCopiedRecipient(Document document, Long userId);

    boolean canViewAttachments(Document document, Long userId);

    boolean canUploadAttachment(Document document, Long userId);

    boolean canDeleteOwnAttachment(Document document, Long userId);

    boolean canViewTimeline(Document document, Long userId);

    boolean canViewMinutes(Document document, Long userId);

    boolean canManageRecipients(Document document, Long userId);

    RecipientSummaryResponse summaryForViewer(Document document, Long viewerUserId);

    RecipientSummaryResponse summaryForMovement(Document document, Long movementId, Long viewerUserId);

    RecipientSummaryResponse summaryForSentMovement(Document document, Long movementId, Long viewerUserId);

    List<DocumentRecipient> activeRecipients(Long documentId);

    Optional<Long> activeRecipientSetId(Long documentId);

    Map<String, List<Long>> getActiveRecipientsByType(Long documentId);

    Map<Long, Optional<RecipientType>> activeRecipientTypesForUser(Collection<Long> documentIds, Long userId);

    Map<Long, RecipientSummaryResponse> summaryBatchForViewer(Collection<Long> documentIds, Long viewerUserId);
}
