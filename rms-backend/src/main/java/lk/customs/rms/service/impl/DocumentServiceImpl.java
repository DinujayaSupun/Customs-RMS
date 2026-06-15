package lk.customs.rms.service.impl;

import lk.customs.rms.dto.*;
import lk.customs.rms.entity.Document;
import lk.customs.rms.entity.DocumentAttachment;
import lk.customs.rms.entity.DocumentMovement;
import lk.customs.rms.entity.DocumentRemark;
import lk.customs.rms.entity.DocumentUserView;
import lk.customs.rms.entity.AuditLog;
import lk.customs.rms.entity.User;
import lk.customs.rms.enums.AppPermission;
import lk.customs.rms.enums.MovementActionType;
import lk.customs.rms.enums.Status;
import lk.customs.rms.exception.BadRequestException;
import lk.customs.rms.exception.ResourceNotFoundException;
import lk.customs.rms.repository.DocumentMovementRepository;
import lk.customs.rms.repository.DocumentAttachmentRepository;
import lk.customs.rms.repository.DocumentRemarkRepository;
import lk.customs.rms.repository.DocumentRepository;
import lk.customs.rms.repository.DocumentUserViewRepository;
import lk.customs.rms.repository.AuditLogRepository;
import lk.customs.rms.repository.UserRepository;
import lk.customs.rms.service.AuditLogService;
import lk.customs.rms.service.DcAutoForwardConfigService;
import lk.customs.rms.service.DocumentService;
import lk.customs.rms.service.PermissionService;
import lk.customs.rms.service.RealtimeNotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/*
 * ==========================================================
 * FILE: DocumentServiceImpl.java
 *
 * PURPOSE:
 *   Implements Sri Lanka Customs Document Workflow rules.
 *
 * IMPORTANT BUSINESS RULES:
 *   1) Ownership rule:
 *      - Only CURRENT OWNER can forward/return/add remarks.
 *
 *   2) Permission-controlled decisions:
 *      - Approve / Reject / Issue / Reopen depend on the assigned role permissions.
 *      - User must still be the current owner to do those actions.
 *
 *   3) FINAL STATE LOCK (critical):
 *      - Once ISSUED or REJECTED => NO further workflow actions allowed.
 *      - If APPROVED => ONLY ISSUE is allowed (no forward/return/approve/reject).
 *      - ISSUE allowed ONLY when status == APPROVED.
 *
 *   4) REOPEN (NEW):
 *      - Only users with reopen permission can REOPEN an APPROVED or REJECTED document.
 *      - Not allowed if ISSUED (final).
 *      - Requires a reason (remarkText must not be empty).
 *      - Sets status back to IN_PROGRESS and clears completedAt.
 *      - Logs Movement(REOPEN) + Audit log + Remark.
 *
 *   5) Remarks:
 *      - Can be added standalone OR during workflow action.
 *      - Always saved BEFORE ownership changes.
 * ==========================================================
 */
@Service
public class DocumentServiceImpl implements DocumentService {

    private record UndoSendState(
            boolean canUndo,
            String status,
            LocalDateTime expiresAt,
            boolean receiverOpened,
            String actionType,
            boolean requiresReason,
            boolean showExpiredInfo
    ) {}

    private final DocumentRepository documentRepository;
    private final DocumentAttachmentRepository attachmentRepository;
    private final DocumentMovementRepository movementRepository;
    private final DocumentRemarkRepository remarkRepository;
    private final DocumentUserViewRepository documentUserViewRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditLogService auditLogService;
    private final PermissionService permissionService;
    private final RealtimeNotificationService realtimeNotificationService;
    private final DcAutoForwardConfigService dcAutoForwardConfigService;

    public DocumentServiceImpl(
            DocumentRepository documentRepository,
            DocumentAttachmentRepository attachmentRepository,
            DocumentMovementRepository movementRepository,
            DocumentRemarkRepository remarkRepository,
            DocumentUserViewRepository documentUserViewRepository,
            UserRepository userRepository,
            AuditLogRepository auditLogRepository,
            AuditLogService auditLogService,
                PermissionService permissionService,
                RealtimeNotificationService realtimeNotificationService,
                DcAutoForwardConfigService dcAutoForwardConfigService
    ) {
        this.documentRepository = documentRepository;
        this.attachmentRepository = attachmentRepository;
        this.movementRepository = movementRepository;
        this.remarkRepository = remarkRepository;
        this.documentUserViewRepository = documentUserViewRepository;
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
        this.auditLogService = auditLogService;
        this.permissionService = permissionService;
        this.realtimeNotificationService = realtimeNotificationService;
        this.dcAutoForwardConfigService = dcAutoForwardConfigService;
    }

    // ==========================================================
    // DOCUMENT CRUD
    // ==========================================================

    @Override
    public DocumentResponse createDocument(CreateDocumentRequest request, Long actorUserId) {
        permissionService.ensurePermission(actorUserId, AppPermission.CREATE_DOCUMENT, "You are not allowed to create documents.");

        // Unique constraint check (business)
        if (documentRepository.existsByRefNoAndDeletedFalse(request.getRefNo())) {
            throw new BadRequestException("Ref No already exists: " + request.getRefNo());
        }

        User createdBy = requireUser(actorUserId);
        User owner = createdBy;

        Document doc = new Document();
        doc.setRefNo(request.getRefNo());
        doc.setTitle(request.getTitle());
        doc.setReceivedDate(request.getReceivedDate());
        doc.setCompanyName(request.getCompanyName());
        doc.setPriority(request.getPriority());
        doc.setVisibility("PRIVATE");

        // Document starts as PENDING
        doc.setStatus(Status.PENDING);

        doc.setCreatedByUserId(createdBy.getId());
        doc.setCurrentOwnerUserId(createdBy.getId());
        LocalDateTime now = LocalDateTime.now();
        doc.setCreatedAt(now);
        doc.setUpdatedAt(now);
        doc.setDeleted(false);

        Document saved = documentRepository.save(doc);

        // Movement: CREATE (file is born)
        DocumentMovement mv = DocumentMovement.create(
                saved.getId(),
                null,
                saved.getCurrentOwnerUserId(),
                createdBy.getId(),
                MovementActionType.CREATE
        );
        movementRepository.save(mv);

        auditLogService.logDocumentCreate(saved.getId(), createdBy.getId(), "Document created");

        return DocumentResponse.from(DocumentResponse.mapping(saved)
                .createdByName(createdBy.getFullName())
                .ownerName(owner.getFullName())
                .build());
    }

    @Override
    public Page<DocumentResponse> getDocuments(int page, int size, String search, Long actorUserId) {
        var pageable = PageRequest.of(
            page,
            size,
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );

        boolean canViewAll = permissionService.hasPermission(actorUserId, AppPermission.VIEW_ALL_DOCUMENTS);
        boolean canViewPublic = permissionService.hasPermission(actorUserId, AppPermission.VIEW_PUBLIC_DOCUMENT);
        boolean canViewPrivate = permissionService.hasPermission(actorUserId, AppPermission.VIEW_PRIVATE_DOCUMENT);
        boolean canViewOwnCreated = permissionService.hasPermission(actorUserId, AppPermission.VIEW_OWN_CREATED_DOCUMENTS);

        Page<Document> docs;
        if (canViewAll) {
            if (search == null || search.isBlank()) {
                docs = documentRepository.findAllNotDeleted(pageable);
            } else {
                docs = documentRepository.searchNotDeleted(search.trim(), pageable);
            }
        } else {
            if (search == null || search.isBlank()) {
                docs = documentRepository.findAccessibleNotDeleted(
                        actorUserId,
                        canViewPublic,
                        canViewPrivate,
                        canViewOwnCreated,
                        MovementActionType.FORWARD,
                        pageable
                );
            } else {
                docs = documentRepository.searchAccessibleNotDeleted(
                        search.trim(),
                        actorUserId,
                        canViewPublic,
                        canViewPrivate,
                        canViewOwnCreated,
                        MovementActionType.FORWARD,
                        pageable
                );
            }
        }

        return toDocumentResponsePage(docs, actorUserId);
    }

    @Override
    public Page<DocumentResponse> getMyInboxDocuments(int page, int size, Long actorUserId) {
        var pageable = PageRequest.of(
            page,
            size,
            Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );

        Page<Document> docs = documentRepository.findAssignedActiveByOwner(actorUserId, Status.ISSUED, pageable);
        return toDocumentResponsePage(docs, actorUserId);
    }

    private Page<DocumentResponse> toDocumentResponsePage(Page<Document> docs, Long actorUserId) {
        List<Long> docIds = docs.getContent().stream().map(Document::getId).toList();
        Set<Long> viewedDocIds = docIds.isEmpty()
            ? Set.of()
            : new HashSet<>(documentUserViewRepository.findViewedDocumentIdsByUser(actorUserId, docIds));
        Map<Long, String> mainAttachmentTypes = docIds.isEmpty()
            ? Map.of()
            : attachmentRepository
                .findByDocumentIdInAndDeletedFalseAndIsLatestTrue(docIds)
                .stream()
                .collect(Collectors.toMap(
                    DocumentAttachment::getDocumentId,
                    a -> resolveAttachmentTypeFromFileName(a.getFileName()),
                    (first, second) -> first
                ));
        Map<Long, DocumentRemark> latestRemarks = docIds.isEmpty()
            ? Map.of()
            : remarkRepository.findLatestByDocumentIdsWithUser(docIds)
                .stream()
                .collect(Collectors.toMap(
                    DocumentRemark::getDocumentId,
                    remark -> remark,
                    (first, second) -> first
                ));
        Map<Long, DocumentMovement> latestInboundByDoc = docIds.isEmpty()
            ? Map.of()
            : movementRepository.findLatestInboundByActorAndDocumentIds(
                    actorUserId,
                    docIds,
                    List.of(MovementActionType.CREATE, MovementActionType.FORWARD, MovementActionType.RETURN, MovementActionType.UNDO_SEND)
                )
                .stream()
                .collect(Collectors.toMap(
                    DocumentMovement::getDocumentId,
                    movement -> movement,
                    (first, second) -> first
                ));

        return docs.map(d -> {
            String createdByName = userRepository.findById(d.getCreatedByUserId()).map(User::getFullName).orElse(null);
            String ownerName = userRepository.findById(d.getCurrentOwnerUserId()).map(User::getFullName).orElse(null);
            boolean viewedByMe = viewedDocIds.contains(d.getId());
            DocumentRemark latestRemark = canViewRemarks(d, actorUserId) ? latestRemarks.get(d.getId()) : null;
            String latestRemarkPreview = latestRemark == null ? null : toRemarkPreview(latestRemark);
            DocumentMovement latestInbound = latestInboundByDoc.get(d.getId());
            Long inboxSenderUserId = resolveInboxSenderUserId(latestInbound);
            User inboxSender = inboxSenderUserId == null ? null : userRepository.findById(inboxSenderUserId).orElse(null);
            boolean undoInboxMovement = latestInbound != null && latestInbound.getActionType() == MovementActionType.UNDO_SEND;
            // Inbox rows show undo notices as read-only status, not as a new action the receiver can undo again.
            User undoActor = undoInboxMovement && latestInbound.getActionByUserId() != null
                ? userRepository.findById(latestInbound.getActionByUserId()).orElse(null)
                : null;
            User undoFrom = undoInboxMovement && latestInbound.getFromUserId() != null
                ? userRepository.findById(latestInbound.getFromUserId()).orElse(null)
                : null;
            return DocumentResponse.from(DocumentResponse.mapping(d)
                .createdByName(createdByName)
                .ownerName(ownerName)
                .mainAttachmentType(mainAttachmentTypes.get(d.getId()))
                .latestRemarkPreview(latestRemarkPreview)
                .viewedByMe(viewedByMe)
                .inboxReceivedAt(latestInbound == null ? null : latestInbound.getActionAt())
                .inboxSenderUserId(inboxSenderUserId)
                .inboxSenderName(inboxSender == null ? null : inboxSender.getFullName())
                .inboxSenderRole(roleName(inboxSender))
                .latestRemarkByUserId(latestRemark == null ? null : latestRemark.getRemarkedByUserId())
                .latestRemarkByName(latestRemark == null || latestRemark.getRemarkedBy() == null ? null : latestRemark.getRemarkedBy().getFullName())
                .latestRemarkByRole(latestRemark == null ? null : roleName(latestRemark.getRemarkedBy()))
                .latestRemarkTextPreview(latestRemark == null ? null : toRemarkPreview(latestRemark.getRemarkText()))
                .latestRemarkText(latestRemark == null ? null : latestRemark.getRemarkText())
                .latestRemarkAt(latestRemark == null ? null : latestRemark.getRemarkedAt())
                .canUndoSend(false)
                .undoSendStatus(undoInboxMovement ? "UNDONE" : null)
                .undoSendReceiverOpened(false)
                .undoSendActionType(undoInboxMovement ? "UNDO_SEND" : null)
                .undoSendRequiresReason(false)
                .undoSendShowExpiredInfo(false)
                .undoSendByUserId(undoInboxMovement ? latestInbound.getActionByUserId() : null)
                .undoSendByName(undoActor == null ? null : undoActor.getFullName())
                .undoSendByRole(roleName(undoActor))
                .undoSendFromUserId(undoInboxMovement ? latestInbound.getFromUserId() : null)
                .undoSendFromName(undoFrom == null ? null : undoFrom.getFullName())
                .undoSendFromRole(roleName(undoFrom))
                .build());
        });
    }

    @Override
    public Page<SentMessageResponse> getSentMessages(int page, int size, String search, Long actorUserId) {
        permissionService.ensurePermission(actorUserId, AppPermission.VIEW_SENT_MESSAGES, "You are not allowed to view sent messages.");

        var pageable = PageRequest.of(page, size);
        String normalizedSearch = search == null ? "" : search.trim().toLowerCase();

        List<DocumentMovement> actorMovements = movementRepository
            .findByActionTypeInAndActionByUserIdOrderByActionAtDescIdDesc(
                List.of(MovementActionType.FORWARD, MovementActionType.RETURN),
                actorUserId
            );
        List<DocumentMovement> undoNotices = movementRepository
            .findByActionTypeAndFromUserIdOrderByActionAtDescIdDesc(MovementActionType.UNDO_SEND, actorUserId);
        // Sent mail includes both actions the user performed and undo notices routed back to them.
        actorMovements = java.util.stream.Stream.concat(actorMovements.stream(), undoNotices.stream())
            .sorted((a, b) -> {
                LocalDateTime aTime = a.getActionAt();
                LocalDateTime bTime = b.getActionAt();
                int timeCompare = java.util.Comparator.nullsLast(LocalDateTime::compareTo).compare(bTime, aTime);
                if (timeCompare != 0) return timeCompare;
                return java.util.Comparator.nullsLast(Long::compareTo).compare(b.getId(), a.getId());
            })
            .toList();

        List<Long> docIds = actorMovements.stream().map(DocumentMovement::getDocumentId).distinct().toList();
        Map<Long, Document> docsById = docIds.isEmpty()
            ? Map.of()
            : documentRepository.findAllById(docIds)
                .stream()
                .filter(d -> !d.isDeleted())
                .collect(Collectors.toMap(Document::getId, d -> d));

        List<DocumentMovement> filteredMovements = actorMovements.stream()
            .filter(m -> docsById.containsKey(m.getDocumentId()))
            .filter(m -> {
                if (normalizedSearch.isEmpty()) return true;
                Document d = docsById.get(m.getDocumentId());
                String ref = d == null || d.getRefNo() == null ? "" : d.getRefNo().toLowerCase();
                String title = d == null || d.getTitle() == null ? "" : d.getTitle().toLowerCase();
                String company = d == null || d.getCompanyName() == null ? "" : d.getCompanyName().toLowerCase();
                return ref.contains(normalizedSearch)
                    || title.contains(normalizedSearch)
                    || company.contains(normalizedSearch);
            })
            .toList();

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), filteredMovements.size());
        if (start > end) {
            start = end;
        }
        List<DocumentMovement> pageMovements = filteredMovements.subList(start, end);

        List<Long> toUserIds = pageMovements.stream()
            .map(DocumentMovement::getToUserId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();
        Map<Long, String> userNamesById = toUserIds.isEmpty()
            ? Map.of()
            : userRepository.findAllById(toUserIds)
                .stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));

        List<Long> pageDocIds = pageMovements.stream().map(DocumentMovement::getDocumentId).distinct().toList();

        Map<Long, String> mainAttachmentTypes = pageDocIds.isEmpty()
            ? Map.of()
            : attachmentRepository
                .findByDocumentIdInAndDeletedFalseAndIsLatestTrue(pageDocIds)
                .stream()
                .collect(Collectors.toMap(
                    DocumentAttachment::getDocumentId,
                    a -> resolveAttachmentTypeFromFileName(a.getFileName()),
                    (first, second) -> first
                ));

        Map<Long, List<DocumentMovement>> inboundByDoc = pageDocIds.isEmpty()
            ? Map.of()
            : movementRepository
                .findByDocumentIdInAndToUserIdAndActionTypeInOrderByDocumentIdAscActionAtAsc(
                    pageDocIds,
                    actorUserId,
                    List.of(MovementActionType.CREATE, MovementActionType.FORWARD, MovementActionType.RETURN)
                )
                .stream()
                .collect(Collectors.groupingBy(DocumentMovement::getDocumentId));

        Map<Long, List<DocumentRemark>> ownRemarksByDoc = pageDocIds.isEmpty()
            ? Map.of()
            : remarkRepository
                .findByDocumentIdInAndRemarkedByUserIdOrderByDocumentIdAscRemarkedAtAsc(pageDocIds, actorUserId)
                .stream()
                .collect(Collectors.groupingBy(DocumentRemark::getDocumentId));
        Map<Long, List<AuditLog>> autoForwardLogsByDoc = pageDocIds.isEmpty()
            ? Map.of()
            : auditLogRepository
                .findByEntityTypeAndEntityIdInAndActionTypeOrderByPerformedAtAsc("MOVEMENT", pageDocIds, "AUTO_FORWARD_DC_TIMEOUT")
                .stream()
                .collect(Collectors.groupingBy(AuditLog::getEntityId));
        // Undo rows are derived from movement history so the sent list can explain why a send is no longer reversible.
        List<DocumentMovement> undoMovements = pageDocIds.isEmpty()
            ? List.of()
            : movementRepository.findByDocumentIdInAndActionTypeOrderByDocumentIdAscActionAtAsc(
                pageDocIds,
                MovementActionType.UNDO_SEND
            );
        Map<Long, List<DocumentMovement>> undoMovementsByDoc = undoMovements.stream()
            .collect(Collectors.groupingBy(DocumentMovement::getDocumentId));
        List<Long> undoActorIds = undoMovements.stream()
            .map(DocumentMovement::getActionByUserId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();
        Map<Long, User> undoActorsById = undoActorIds.isEmpty()
            ? Map.of()
            : userRepository.findAllById(undoActorIds)
                .stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<SentMessageResponse> sentRows = pageMovements.stream().map(movement -> {
            Document doc = docsById.get(movement.getDocumentId());
            String ownMinutePreview = toOwnSentMinutePreview(
                movement,
                inboundByDoc.getOrDefault(movement.getDocumentId(), List.of()),
                ownRemarksByDoc.getOrDefault(movement.getDocumentId(), List.of())
            );
            if (!canViewRemarks(doc, actorUserId)) {
                ownMinutePreview = null;
            }
            boolean autoForwarded = autoForwardLogsByDoc
                .getOrDefault(movement.getDocumentId(), List.of())
                .stream()
                .anyMatch(log -> log.getPerformedAt() != null
                    && movement.getActionAt() != null
                    && !log.getPerformedAt().isBefore(movement.getActionAt()));
            boolean undoNotice = movement.getActionType() == MovementActionType.UNDO_SEND;
            UndoSendState undoState = undoNotice
                ? new UndoSendState(false, "UNDONE", null, false, "UNDO_SEND", false, true)
                : resolveUndoSendState(doc, movement, actorUserId);
            DocumentMovement undoMovement = undoNotice
                ? movement
                : findUndoMovementForSentMovement(
                    movement,
                    undoMovementsByDoc.getOrDefault(movement.getDocumentId(), List.of())
                );
            User undoActor = undoMovement == null ? null : undoActorsById.get(undoMovement.getActionByUserId());
            return SentMessageResponse.builder()
                .movementId(movement.getId())
                .documentId(movement.getDocumentId())
                .refNo(doc == null ? null : doc.getRefNo())
                .title(doc == null ? null : doc.getTitle())
                .companyName(doc == null ? null : doc.getCompanyName())
                .priority(doc == null ? null : doc.getPriority())
                .status(doc == null ? null : doc.getStatus())
                .mainAttachmentType(mainAttachmentTypes.get(movement.getDocumentId()))
                .forwardVisibility(movement.getForwardVisibility())
                .toUserId(movement.getToUserId())
                .toUserName(userNamesById.get(movement.getToUserId()))
                .latestRemarkPreview(ownMinutePreview)
                .sentAt(movement.getActionAt())
                .autoForwarded(autoForwarded)
                .canUndoSend(undoState.canUndo())
                .undoSendStatus(undoState.status())
                .undoSendExpiresAt(undoState.expiresAt())
                .undoSendReceiverOpened(undoState.receiverOpened())
                .undoSendActionType(undoState.actionType())
                .undoSendRequiresReason(undoState.requiresReason())
                .undoSendShowExpiredInfo(undoState.showExpiredInfo())
                .undoSendByUserId(undoMovement == null ? null : undoMovement.getActionByUserId())
                .undoSendByName(undoActor == null ? null : undoActor.getFullName())
                .undoSendByRole(undoActor == null || undoActor.getRole() == null ? null : undoActor.getRole().getRoleName())
                .build();
            }).toList();

            return new PageImpl<>(sentRows, pageable, filteredMovements.size());
    }

    private DocumentMovement findUndoMovementForSentMovement(DocumentMovement sentMovement, List<DocumentMovement> undoMovements) {
        if (sentMovement == null || undoMovements == null || undoMovements.isEmpty()) {
            return null;
        }
        return undoMovements.stream()
            .filter(undo -> java.util.Objects.equals(undo.getActionByUserId(), sentMovement.getActionByUserId()))
            .filter(undo -> java.util.Objects.equals(undo.getFromUserId(), sentMovement.getToUserId()))
            .filter(undo -> java.util.Objects.equals(undo.getToUserId(), sentMovement.getFromUserId()))
            .filter(undo -> undo.getActionAt() != null
                && sentMovement.getActionAt() != null
                && !undo.getActionAt().isBefore(sentMovement.getActionAt()))
            .findFirst()
            .orElse(null);
    }

    @Override
    public DocumentResponse getDocumentById(Long id, Long actorUserId) {
        Document d = requireDocument(id);
        User actor = requireUser(actorUserId);
        ensureCanViewDocument(d, actorUserId);
        // Opening the document marks it as viewed for workload counts and undo-send receiver-open checks.
        markViewedByUser(d.getId(), actorUserId);
        markDcViewedIfNeeded(d, actor);

        String createdByName = userRepository.findById(d.getCreatedByUserId()).map(User::getFullName).orElse(null);
        String ownerName = userRepository.findById(d.getCurrentOwnerUserId()).map(User::getFullName).orElse(null);
        String mainAttachmentType = resolveMainAttachmentType(d.getId());
        String latestRemarkPreview = canViewRemarks(d, actorUserId)
            ? remarkRepository.findFirstByDocumentIdOrderByRemarkedAtDesc(d.getId())
                .map(this::toRemarkPreview)
                .orElse(null)
            : null;
        UndoSendState undoState = movementRepository.findFirstByDocumentIdOrderByActionAtDescIdDesc(d.getId())
                .map(movement -> resolveUndoSendState(d, movement, actorUserId))
                .orElse(resolveUndoSendState(d, null, actorUserId));

        return DocumentResponse.from(DocumentResponse.mapping(d)
                .createdByName(createdByName)
                .ownerName(ownerName)
                .mainAttachmentType(mainAttachmentType)
                .latestRemarkPreview(latestRemarkPreview)
                .viewedByMe(true)
                .canUndoSend(undoState.canUndo())
                .undoSendStatus(undoState.status())
                .undoSendExpiresAt(undoState.expiresAt())
                .undoSendReceiverOpened(undoState.receiverOpened())
                .undoSendActionType(undoState.actionType())
                .undoSendRequiresReason(undoState.requiresReason())
                .undoSendShowExpiredInfo(undoState.showExpiredInfo())
                .build());
    }

    @Override
    public MyWorkloadStatsResponse getMyWorkloadStats(Long actorUserId) {
        long assignedCount = documentRepository.countAssignedActiveByOwner(actorUserId, Status.ISSUED);
        long openedCount = documentRepository.countOpenedAssignedActiveByOwner(actorUserId, Status.ISSUED);
        long unopenedCount = Math.max(0, assignedCount - openedCount);

        return MyWorkloadStatsResponse.builder()
                .assignedCount(assignedCount)
                .openedCount(openedCount)
                .unopenedCount(unopenedCount)
                .build();
    }

    @Override
    public DocumentResponse updateDocument(Long id, UpdateDocumentRequest request, Long actorUserId) {
        Document d = requireDocument(id);

        permissionService.ensurePermission(actorUserId, AppPermission.EDIT_DOCUMENT_DETAILS, "You are not allowed to edit document details.");

        // Only the current owner can edit details
        if (!d.getCurrentOwnerUserId().equals(actorUserId)) {
            throw new BadRequestException("Only the current owner can edit document details.");
        }

        // Lock editing after completion/issue
        if (d.getCompletedAt() != null || d.getStatus() == Status.ISSUED) {
            throw new BadRequestException("Cannot edit details after document is COMPLETED or ISSUED.");
        }

        String newRefNo = request.getRefNo() == null ? "" : request.getRefNo().trim();
        if (newRefNo.isEmpty()) {
            throw new BadRequestException("Ref No is required.");
        }

        if (documentRepository.existsByRefNoAndDeletedFalseAndIdNot(newRefNo, d.getId())) {
            throw new BadRequestException("Ref No already exists: " + newRefNo);
        }

        d.setRefNo(newRefNo);
        d.setTitle(request.getTitle().trim());
        d.setCompanyName(request.getCompanyName().trim());
        d.setReceivedDate(request.getReceivedDate());
        d.setPriority(request.getPriority());
        touchDocument(d);

        Document saved = documentRepository.save(d);

        auditLogService.logDocumentUpdate(saved.getId(), actorUserId, "Document updated");

        String createdByName = userRepository.findById(saved.getCreatedByUserId()).map(User::getFullName).orElse(null);
        String ownerName = userRepository.findById(saved.getCurrentOwnerUserId()).map(User::getFullName).orElse(null);
        String mainAttachmentType = resolveMainAttachmentType(saved.getId());

        String latestRemarkPreview = remarkRepository.findFirstByDocumentIdOrderByRemarkedAtDesc(saved.getId())
            .map(this::toRemarkPreview)
            .orElse(null);

        return DocumentResponse.from(DocumentResponse.mapping(saved)
                .createdByName(createdByName)
                .ownerName(ownerName)
                .mainAttachmentType(mainAttachmentType)
                .latestRemarkPreview(latestRemarkPreview)
                .viewedByMe(true)
                .build());
    }

    private String resolveMainAttachmentType(Long documentId) {
        return attachmentRepository
                .findFirstByDocumentIdAndDeletedFalseAndIsLatestTrue(documentId)
                .map(DocumentAttachment::getFileName)
                .map(this::resolveAttachmentTypeFromFileName)
                .orElse(null);
    }

    private String resolveAttachmentTypeFromFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) return null;
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) return "FILE";

        String ext = fileName.substring(dot + 1).toLowerCase();
        return switch (ext) {
            case "pdf" -> "PDF";
            case "doc", "docx" -> "DOC";
            case "xls", "xlsx", "csv" -> "XLS";
            case "png", "jpg", "jpeg", "gif", "bmp", "webp" -> "IMG";
            case "txt" -> "TXT";
            case "zip", "rar", "7z" -> "ZIP";
            default -> "FILE";
        };
    }

    @Override
    public void deleteDocument(Long id, Long actorUserId) {
        permissionService.ensurePermission(actorUserId, AppPermission.DELETE_DOCUMENT, "You are not allowed to delete documents.");

        Document d = requireDocument(id);

        d.setDeleted(true);
        d.setDeletedAt(LocalDateTime.now());
        d.setDeletedByUserId(actorUserId);
        documentRepository.save(d);

        auditLogService.logDocumentDelete(id, actorUserId, "Document deleted (soft)");
    }

    // ==========================================================
    // WORKFLOW ACTIONS
    // ==========================================================

    @Override
    @Transactional
    public void forward(Long documentId, ForwardReturnRequest request, Long actorUserId) {
        permissionService.ensurePermission(actorUserId, AppPermission.FORWARD_DOCUMENT, "You are not allowed to forward documents.");

        Document d = requireDocument(documentId);

        // FINAL STATE LOCK: cannot forward if already final / approved
        ensureCanForwardOrReturn(d);

        User actionBy = requireUser(actorUserId);
        User toUser = requireUser(request.getToUserId());

        // Ownership check
        if (!d.getCurrentOwnerUserId().equals(actionBy.getId())) {
            throw new BadRequestException("Only the current owner can forward this document.");
        }

        String forwardVisibility = normalizeForwardVisibility(request.getForwardVisibility());
        if ("PRIVATE".equals(forwardVisibility)) {
            permissionService.ensurePermission(actorUserId, AppPermission.FORWARD_PRIVATE, "You are not allowed to forward as PRIVATE.");
        } else {
            permissionService.ensurePermission(actorUserId, AppPermission.FORWARD_PUBLIC, "You are not allowed to forward as PUBLIC.");
        }
        if (!forwardVisibility.equalsIgnoreCase(effectiveVisibility(d))) {
            permissionService.ensurePermission(actorUserId, AppPermission.CHANGE_DOCUMENT_VISIBILITY, "You are not allowed to change document visibility.");
        }

        // IMPORTANT: remark must be saved BEFORE ownership changes
        saveRemarkIfPresent(d, actionBy.getId(), request.getRemarkText(), "Remark added during forward");

        Long from = d.getCurrentOwnerUserId();
        Long to = toUser.getId();

        d.setCurrentOwnerUserId(to);
        // New owner should see the document as unopened until they explicitly open it.
        documentUserViewRepository.deleteByDocumentIdAndUserId(d.getId(), to);
        d.setVisibility(forwardVisibility);
        applyDcAutoForwardTrackingAfterOwnershipChange(d, toUser);
        d.setStatus(Status.IN_PROGRESS);
        touchDocument(d);
        documentRepository.save(d);

        DocumentMovement mv = DocumentMovement.create(documentId, from, to, actionBy.getId(), MovementActionType.FORWARD, forwardVisibility);
        movementRepository.save(mv);

        auditLogService.logMovement(documentId, actionBy.getId(), "FORWARD", "Forwarded to userId=" + to + " with visibility=" + forwardVisibility);
        realtimeNotificationService.notifyDocumentForwarded(
            to,
            d.getId(),
            d.getRefNo(),
            d.getTitle(),
            actionBy.getId(),
            actionBy.getFullName()
        );
    }

    @Override
    @Transactional
    public void returns(Long documentId, ForwardReturnRequest request, Long actorUserId) {
        permissionService.ensurePermission(actorUserId, AppPermission.RETURN_DOCUMENT, "You are not allowed to return documents.");

        Document d = requireDocument(documentId);

        // FINAL STATE LOCK
        ensureCanForwardOrReturn(d);

        User actionBy = requireUser(actorUserId);
        User toUser = requireUser(request.getToUserId());

        // Ownership check
        if (!d.getCurrentOwnerUserId().equals(actionBy.getId())) {
            throw new BadRequestException("Only the current owner can return this document.");
        }

        // Save remark BEFORE ownership change
        saveRemarkIfPresent(d, actionBy.getId(), request.getRemarkText(), "Remark added during return");

        Long from = d.getCurrentOwnerUserId();
        Long to = toUser.getId();

        d.setCurrentOwnerUserId(to);
        // Return also creates a fresh unread inbox item for the receiver.
        documentUserViewRepository.deleteByDocumentIdAndUserId(d.getId(), to);
        applyDcAutoForwardTrackingAfterOwnershipChange(d, toUser);
        d.setStatus(Status.RETURNED);
        touchDocument(d);
        documentRepository.save(d);

        DocumentMovement mv = DocumentMovement.create(documentId, from, to, actionBy.getId(), MovementActionType.RETURN);
        movementRepository.save(mv);

        auditLogService.logMovement(documentId, actionBy.getId(), "RETURN", "Returned to userId=" + to);
        realtimeNotificationService.notifyDocumentReturned(
            to,
            d.getId(),
            d.getRefNo(),
            d.getTitle(),
            actionBy.getId(),
            actionBy.getFullName()
        );
    }

    @Override
    @Transactional
    public void undoSend(Long documentId, UndoSendRequest request, Long actorUserId) {
        Document d = requireDocument(documentId);
        User actor = requireUser(actorUserId);
        DocumentMovement latestMovement = movementRepository.findFirstByDocumentIdOrderByActionAtDescIdDesc(documentId)
                .orElseThrow(() -> new BadRequestException("No sent movement found to undo."));

        UndoSendState state = resolveUndoSendState(d, latestMovement, actorUserId);
        if (!state.canUndo()) {
            throw new BadRequestException(undoBlockedMessage(state.status()));
        }

        String reason = request == null || request.getReason() == null ? "" : request.getReason().trim();
        if (state.requiresReason() && reason.isEmpty()) {
            throw new BadRequestException("Undo Send requires a reason.");
        }

        // Undo Send reverses only the latest forward/return movement and restores ownership to its sender.
        Long receiverUserId = latestMovement.getToUserId();
        Long senderUserId = latestMovement.getFromUserId();
        if (senderUserId == null) {
            throw new BadRequestException("Cannot undo this movement because the sender is unknown.");
        }

        d.setCurrentOwnerUserId(senderUserId);
        d.setStatus(Status.IN_PROGRESS);
        d.setCompletedAt(null);
        documentUserViewRepository.deleteByDocumentIdAndUserId(d.getId(), senderUserId);
        applyDcAutoForwardTrackingAfterOwnershipChange(d, actor);
        touchDocument(d);
        documentRepository.save(d);

        DocumentMovement undoMovement = DocumentMovement.create(
                documentId,
                receiverUserId,
                senderUserId,
                actorUserId,
                MovementActionType.UNDO_SEND,
                latestMovement.getForwardVisibility()
        );
        movementRepository.save(undoMovement);

        auditLogService.logMovement(
                documentId,
                actorUserId,
                "UNDO_SEND",
                "Undo send from userId=" + receiverUserId + " back to userId=" + senderUserId
                        + (reason.isBlank() ? "" : ". Reason: " + reason)
        );

        var config = dcAutoForwardConfigService.getOrCreateEntity();
        if (config.isUndoSendNotifyReceiver()) {
            realtimeNotificationService.notifyDocumentUndoSend(
                    receiverUserId,
                    d.getId(),
                    d.getRefNo(),
                    d.getTitle(),
                    actor.getId(),
                    actor.getFullName()
            );
        }
        realtimeNotificationService.notifyDocumentUndoReturnedToSender(
                senderUserId,
                d.getId(),
                d.getRefNo(),
                d.getTitle(),
                actor.getId(),
                actor.getFullName()
        );
    }

    @Override
    public void approve(Long documentId, DecisionRequest request, Long actorUserId) {
        if (!dcAutoForwardConfigService.isApproveRejectButtonsEnabled()) {
            throw new BadRequestException("Approve action is disabled by admin workflow settings.");
        }
        permissionService.ensurePermission(actorUserId, AppPermission.APPROVE_DOCUMENT, "You are not allowed to approve documents.");

        Document d = requireDocument(documentId);

        // FINAL STATE + transition guard
        ensureCanApproveOrReject(d);

        // DC must be current owner
        if (!d.getCurrentOwnerUserId().equals(actorUserId)) {
            throw new BadRequestException("Only the current owner can approve this document.");
        }

        saveRemarkIfPresent(d, actorUserId, request.getRemarkText(), "Remark added during approve");

        d.setStatus(Status.APPROVED);
        d.setCompletedAt(LocalDateTime.now());
        touchDocument(d);
        documentRepository.save(d);

        DocumentMovement mv = DocumentMovement.create(documentId, d.getCurrentOwnerUserId(), null, actorUserId, MovementActionType.APPROVE);
        movementRepository.save(mv);

        auditLogService.logMovement(documentId, actorUserId, "APPROVE", "Approved by DC");
    }

    @Override
    public void reject(Long documentId, DecisionRequest request, Long actorUserId) {
        if (!dcAutoForwardConfigService.isApproveRejectButtonsEnabled()) {
            throw new BadRequestException("Reject action is disabled by admin workflow settings.");
        }
        permissionService.ensurePermission(actorUserId, AppPermission.REJECT_DOCUMENT, "You are not allowed to reject documents.");

        Document d = requireDocument(documentId);

        // FINAL STATE + transition guard
        ensureCanApproveOrReject(d);

        if (!d.getCurrentOwnerUserId().equals(actorUserId)) {
            throw new BadRequestException("Only the current owner can reject this document.");
        }

        saveRemarkIfPresent(d, actorUserId, request.getRemarkText(), "Remark added during reject");

        d.setStatus(Status.REJECTED);
        d.setCompletedAt(LocalDateTime.now());
        touchDocument(d);
        documentRepository.save(d);

        DocumentMovement mv = DocumentMovement.create(documentId, d.getCurrentOwnerUserId(), null, actorUserId, MovementActionType.REJECT);
        movementRepository.save(mv);

        auditLogService.logMovement(documentId, actorUserId, "REJECT", "Rejected by DC");
    }

    @Override
    public void issue(Long documentId, DecisionRequest request, Long actorUserId) {
        permissionService.ensurePermission(actorUserId, AppPermission.ISSUE_DOCUMENT, "You are not allowed to complete documents.");

        Document d = requireDocument(documentId);

        // ISSUE must happen ONLY after APPROVED
        ensureCanIssue(d);

        if (!d.getCurrentOwnerUserId().equals(actorUserId)) {
            throw new BadRequestException("Only the current owner can complete this document.");
        }

        saveRemarkIfPresent(d, actorUserId, request.getRemarkText(), "Remark added during issue");

        d.setStatus(Status.ISSUED);
        d.setIssuedAt(LocalDateTime.now());
        touchDocument(d);
        documentRepository.save(d);

        DocumentMovement mv = DocumentMovement.create(documentId, d.getCurrentOwnerUserId(), null, actorUserId, MovementActionType.ISSUE);
        movementRepository.save(mv);

        auditLogService.logMovement(documentId, actorUserId, "ISSUE", "Issued by DC");
    }

    // ==========================================================
    // NEW: REOPEN
    // ==========================================================

    @Override
    public void reopen(Long documentId, DecisionRequest request, Long actorUserId) {
        permissionService.ensurePermission(actorUserId, AppPermission.REOPEN_DOCUMENT, "You are not allowed to reopen documents.");

        Document d = requireDocument(documentId);

        if (!dcAutoForwardConfigService.isApproveRejectButtonsEnabled()) {
            if (d.getStatus() != Status.ISSUED && d.getStatus() != Status.APPROVED && d.getStatus() != Status.REJECTED) {
                throw new BadRequestException("Reopen is allowed only for DONE, APPROVED, or REJECTED documents.");
            }
        } else {
            // ISSUED is final - cannot reopen
            if (d.getStatus() == Status.ISSUED) {
                throw new BadRequestException("Cannot reopen an ISSUED document.");
            }

            // Only APPROVED or REJECTED can be reopened
            if (d.getStatus() != Status.APPROVED && d.getStatus() != Status.REJECTED) {
                throw new BadRequestException("Reopen is allowed only for APPROVED or REJECTED documents.");
            }
        }

        // DC must also be the current owner (strong integrity)
        if (!d.getCurrentOwnerUserId().equals(actorUserId)) {
            throw new BadRequestException("Only the current owner can reopen this document.");
        }

        // Reason required
        String reason = request.getRemarkText() == null ? "" : request.getRemarkText().trim();
        if (reason.isEmpty()) {
            throw new BadRequestException("Reopen requires a reason (remarkText must not be empty).");
        }

        // Save reason as remark (security rule inside method = must be current owner)
        saveRemarkIfPresent(d, actorUserId, reason, "Remark added during reopen");

        // Move back to active workflow
        d.setStatus(Status.IN_PROGRESS);

        // Decision is no longer final, so clear completed date
        d.setCompletedAt(null);
        touchDocument(d);

        documentRepository.save(d);

        // Movement: REOPEN (owner unchanged; log from->to as same owner)
        Long owner = d.getCurrentOwnerUserId();
        DocumentMovement mv = DocumentMovement.create(documentId, owner, owner, actorUserId, MovementActionType.REOPEN);
        movementRepository.save(mv);

        auditLogService.logMovement(documentId, actorUserId, "REOPEN", "Reopened by DC");
    }

    // ==========================================================
    // STATUS GUARDS (CRITICAL)
    // ==========================================================

    private void ensureCanForwardOrReturn(Document d) {
        if (!dcAutoForwardConfigService.isForwardReturnAllowed(d.getStatus())) {
            String allowed = dcAutoForwardConfigService.getForwardReturnAllowedStatuses()
                    .stream()
                    .map(Status::name)
                    .collect(Collectors.joining(", "));
            throw new BadRequestException("Cannot forward/return a " + d.getStatus()
                    + " document. Allowed statuses: " + allowed + ".");
        }
    }

    private void ensureCanApproveOrReject(Document d) {
        if (d.getStatus() == Status.ISSUED) {
            throw new BadRequestException("Cannot approve/reject an ISSUED document.");
        }
        if (d.getStatus() == Status.REJECTED) {
            throw new BadRequestException("Cannot approve/reject a REJECTED document.");
        }
        if (d.getStatus() == Status.APPROVED) {
            throw new BadRequestException("Document is already APPROVED. Only ISSUE is allowed.");
        }
    }

    private void ensureCanIssue(Document d) {
        if (!dcAutoForwardConfigService.isApproveRejectButtonsEnabled()) {
            if (d.getStatus() == Status.ISSUED) {
                throw new BadRequestException("Document is already ISSUED.");
            }
            return;
        }
        if (d.getStatus() == Status.ISSUED) {
            throw new BadRequestException("Document is already ISSUED.");
        }
        if (d.getStatus() == Status.REJECTED) {
            throw new BadRequestException("Cannot issue a REJECTED document.");
        }
        if (d.getStatus() != Status.APPROVED) {
            throw new BadRequestException("Cannot issue. Document must be APPROVED first.");
        }
    }

    private UndoSendState resolveUndoSendState(Document doc, DocumentMovement movement, Long actorUserId) {
        var config = dcAutoForwardConfigService.getOrCreateEntity();
        boolean requiresReason = config.isUndoSendRequiresReason();
        boolean showExpiredInfo = config.isUndoSendShowExpiredInfo();

        if (movement == null) {
            return new UndoSendState(false, "NO_SENT_MOVEMENT", null, false, null, requiresReason, showExpiredInfo);
        }

        LocalDateTime sentAt = movement.getActionAt();
        Integer configuredHours = config.getUndoSendWindowHours();
        int windowHours = configuredHours == null || configuredHours < 1 ? 24 : configuredHours;
        LocalDateTime expiresAt = sentAt == null ? null : sentAt.plusHours(windowHours);
        boolean receiverOpened = isReceiverOpenedAfterMovement(movement);
        String actionType = movement.getActionType() == null ? null : movement.getActionType().name();

        // The checks below are ordered from admin/configuration gates to document-state gates for clearer UI reasons.
        if (!config.isUndoSendEnabled()) {
            return new UndoSendState(false, "DISABLED", expiresAt, receiverOpened, actionType, requiresReason, showExpiredInfo);
        }
        if (doc == null || doc.isDeleted()) {
            return new UndoSendState(false, "DOCUMENT_UNAVAILABLE", expiresAt, receiverOpened, actionType, requiresReason, showExpiredInfo);
        }
        if (movement.getActionType() != MovementActionType.FORWARD && movement.getActionType() != MovementActionType.RETURN) {
            return new UndoSendState(false, "ALREADY_MOVED", expiresAt, receiverOpened, actionType, requiresReason, showExpiredInfo);
        }
        if (!dcAutoForwardConfigService.getUndoSendAllowedActions().contains(movement.getActionType())) {
            return new UndoSendState(false, "ACTION_NOT_ALLOWED", expiresAt, receiverOpened, actionType, requiresReason, showExpiredInfo);
        }
        if (movement.getFromUserId() == null || !movement.getFromUserId().equals(actorUserId)) {
            return new UndoSendState(false, "NOT_SENDER", expiresAt, receiverOpened, actionType, requiresReason, showExpiredInfo);
        }
        if (doc.getStatus() == Status.ISSUED || doc.getStatus() == Status.REJECTED || doc.getCompletedAt() != null) {
            return new UndoSendState(false, "FINALIZED", expiresAt, receiverOpened, actionType, requiresReason, showExpiredInfo);
        }
        if (doc.getCurrentOwnerUserId() == null || !doc.getCurrentOwnerUserId().equals(movement.getToUserId())) {
            return new UndoSendState(false, "ALREADY_MOVED", expiresAt, receiverOpened, actionType, requiresReason, showExpiredInfo);
        }
        boolean latestMovement = movementRepository.findFirstByDocumentIdOrderByActionAtDescIdDesc(doc.getId())
                .map(latest -> latest.getId() != null && latest.getId().equals(movement.getId()))
                .orElse(false);
        if (!latestMovement) {
            return new UndoSendState(false, "ALREADY_MOVED", expiresAt, receiverOpened, actionType, requiresReason, showExpiredInfo);
        }
        if (expiresAt != null && LocalDateTime.now().isAfter(expiresAt)) {
            return new UndoSendState(false, "EXPIRED", expiresAt, receiverOpened, actionType, requiresReason, showExpiredInfo);
        }
        if (config.isUndoSendRequiresUnopened() && receiverOpened) {
            return new UndoSendState(false, "OPENED", expiresAt, true, actionType, requiresReason, showExpiredInfo);
        }

        return new UndoSendState(true, "AVAILABLE", expiresAt, receiverOpened, actionType, requiresReason, showExpiredInfo);
    }

    private boolean isReceiverOpenedAfterMovement(DocumentMovement movement) {
        if (movement == null || movement.getDocumentId() == null || movement.getToUserId() == null || movement.getActionAt() == null) {
            return false;
        }
        return documentUserViewRepository.findByDocumentIdAndUserId(movement.getDocumentId(), movement.getToUserId())
                .map(DocumentUserView::getViewedAt)
                .map(viewedAt -> !viewedAt.isBefore(movement.getActionAt()))
                .orElse(false);
    }

    private String undoBlockedMessage(String status) {
        return switch (status == null ? "" : status) {
            case "DISABLED" -> "Undo Send is disabled by admin.";
            case "ACTION_NOT_ALLOWED" -> "Undo Send is not allowed for this action.";
            case "NOT_SENDER" -> "Only the sender can undo this sent document.";
            case "FINALIZED" -> "Cannot undo a finalized document.";
            case "ALREADY_MOVED" -> "Cannot undo because the document has already moved.";
            case "EXPIRED" -> "Undo Send has expired.";
            case "OPENED" -> "Cannot undo because the receiver has already opened the document.";
            default -> "Undo Send is not available for this document.";
        };
    }

    // ==========================================================
    // REMARK HELPER
    // ==========================================================

    private void saveRemarkIfPresent(Document doc, Long actionByUserId, String remarkText, String auditMessage) {
        if (remarkText == null) return;

        permissionService.ensurePermission(actionByUserId, AppPermission.ADD_REMARK, "You are not allowed to add minutes.");

        String text = remarkText.trim();
        if (text.isEmpty()) return;

        // Only current owner can add remark
        if (!doc.getCurrentOwnerUserId().equals(actionByUserId)) {
            throw new BadRequestException("Only the current owner can add remarks.");
        }

        DocumentRemark remark = DocumentRemark.builder()
                .documentId(doc.getId())
                .remarkText(text)
                .remarkedByUserId(actionByUserId)
                .remarkedAt(LocalDateTime.now())
                .build();

        DocumentRemark saved = remarkRepository.save(remark);
        touchDocument(doc);
        documentRepository.save(doc);

        auditLogService.logRemark(doc.getId(), actionByUserId, "REMARK", auditMessage, saved.getId());
    }

    private void touchDocument(Document doc) {
        if (doc == null) return;
        doc.setUpdatedAt(LocalDateTime.now());
    }

    // ==========================================================
    // COMMON HELPERS
    // ==========================================================

    private Document requireDocument(Long documentId) {
        return documentRepository.findByIdAndDeletedFalse(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found: " + userId));
    }

    private boolean isRole(User user, String roleName) {
        return user.getRole() != null && roleName.equalsIgnoreCase(user.getRole().getRoleName());
    }

    private String normalizeForwardVisibility(String visibility) {
        String value = visibility == null ? "" : visibility.trim().toUpperCase();
        if (!"PRIVATE".equals(value) && !"PUBLIC".equals(value)) {
            throw new BadRequestException("forwardVisibility must be PRIVATE or PUBLIC.");
        }
        return value;
    }

    private void ensureCanViewDocument(Document doc, Long actorUserId) {
        if (permissionService.hasPermission(actorUserId, AppPermission.VIEW_ALL_DOCUMENTS)) {
            return;
        }

        if (actorUserId.equals(doc.getCurrentOwnerUserId())) {
            return;
        }

        String visibility = effectiveVisibility(doc);
        if ("PUBLIC".equals(visibility)) {
            if (permissionService.hasPermission(actorUserId, AppPermission.VIEW_PUBLIC_DOCUMENT)) {
                return;
            }
            throw new BadRequestException("You are not allowed to view this public document.");
        }

        if (permissionService.hasPermission(actorUserId, AppPermission.VIEW_OWN_CREATED_DOCUMENTS)
                && actorUserId.equals(doc.getCreatedByUserId())) {
            return;
        }

        if (permissionService.hasPermission(actorUserId, AppPermission.VIEW_PRIVATE_DOCUMENT)) {
            if (actorUserId.equals(doc.getCurrentOwnerUserId())) {
                return;
            }
            if (movementRepository.existsPrivateForwardTrailForUser(doc.getId(), actorUserId, MovementActionType.FORWARD)) {
                return;
            }
        }

        throw new BadRequestException("You are not allowed to view this private document.");
    }

    private String effectiveVisibility(Document doc) {
        String value = doc.getVisibility();
        if (value == null || value.isBlank()) {
            return "PUBLIC";
        }
        return value.trim().toUpperCase();
    }

    private void applyDcAutoForwardTrackingAfterOwnershipChange(Document doc, User toUser) {
        if (isRole(toUser, "DC")) {
            doc.setDcAssignedAt(LocalDateTime.now());
            doc.setDcViewedAt(null);
            return;
        }

        doc.setDcAssignedAt(null);
        doc.setDcViewedAt(null);
    }

    private void markDcViewedIfNeeded(Document doc, User actor) {
        if (!isRole(actor, "DC")) return;
        if (!actor.getId().equals(doc.getCurrentOwnerUserId())) return;
        if (doc.getDcAssignedAt() == null || doc.getDcViewedAt() != null) return;

        doc.setDcViewedAt(LocalDateTime.now());
        documentRepository.save(doc);
    }

    private void markViewedByUser(Long documentId, Long actorUserId) {
        LocalDateTime now = LocalDateTime.now();
        DocumentUserView view = documentUserViewRepository.findByDocumentIdAndUserId(documentId, actorUserId)
                .orElseGet(() -> {
                    DocumentUserView created = new DocumentUserView();
                    created.setDocumentId(documentId);
                    created.setUserId(actorUserId);
                    return created;
                });

        view.setViewedAt(now);
        documentUserViewRepository.save(view);
    }

    private String toOwnSentMinutePreview(DocumentMovement sentMovement,
                                          List<DocumentMovement> inboundMovements,
                                          List<DocumentRemark> ownRemarks) {
        if (sentMovement == null || ownRemarks == null || ownRemarks.isEmpty()) {
            return null;
        }

        LocalDateTime sentAt = sentMovement.getActionAt();
        if (sentAt == null) {
            return null;
        }

        LocalDateTime latestReceivedAt = inboundMovements == null
            ? null
            : inboundMovements.stream()
                .map(DocumentMovement::getActionAt)
                .filter(t -> t != null && !t.isAfter(sentAt))
                .max(LocalDateTime::compareTo)
                .orElse(null);

        DocumentRemark matched = ownRemarks.stream()
            .filter(r -> r.getRemarkedAt() != null)
            .filter(r -> !r.getRemarkedAt().isAfter(sentAt))
            .filter(r -> latestReceivedAt == null || !r.getRemarkedAt().isBefore(latestReceivedAt))
            .max((a, b) -> a.getRemarkedAt().compareTo(b.getRemarkedAt()))
            .orElse(null);

        return matched == null ? null : toRemarkPreview(matched);
    }

    private boolean canViewRemarks(Document document, Long actorUserId) {
        if (document == null || actorUserId == null) {
            return false;
        }
        if (document.getCurrentOwnerUserId() != null && document.getCurrentOwnerUserId().equals(actorUserId)) {
            return true;
        }
        return permissionService.hasPermission(actorUserId, AppPermission.VIEW_REMARKS_WHEN_NOT_REPORT_AT);
    }

    private Long resolveInboxSenderUserId(DocumentMovement movement) {
        if (movement == null) {
            return null;
        }
        if (movement.getFromUserId() != null) {
            return movement.getFromUserId();
        }
        return movement.getActionByUserId();
    }

    private String roleName(User user) {
        return user == null || user.getRole() == null ? null : user.getRole().getRoleName();
    }

    private String toRemarkPreview(String value) {
        String text = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        if (text.isEmpty()) return null;
        if (text.length() <= 110) return text;
        return text.substring(0, 107) + "...";
    }

    private String toRemarkPreview(DocumentRemark remark) {
        if (remark == null || remark.getRemarkText() == null) {
            return null;
        }
        
        String text = remark.getRemarkText().trim().replaceAll("\\s+", " ");
        if (text.isEmpty()) return null;
        
        // Truncate text to 80 chars to fit author/time metadata
        String preview = text.length() <= 80 ? text : text.substring(0, 77) + "...";
        
        // Format author and timestamp
        String authorName = remark.getRemarkedBy() != null ? remark.getRemarkedBy().getFullName() : null;
        if (authorName == null || authorName.isBlank()) {
            authorName = "Unknown";
        }
        String roleName = remark.getRemarkedBy() != null && remark.getRemarkedBy().getRole() != null
            ? remark.getRemarkedBy().getRole().getRoleName()
            : "";
        String authorLabel = (roleName == null || roleName.isBlank())
            ? authorName
            : String.format("%s (%s)", authorName, roleName);
        
        LocalDateTime remarkedAt = remark.getRemarkedAt();
        String formattedTime;
        if (remarkedAt == null) {
            formattedTime = "time unknown";
        } else {
            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("h:mm a");
            formattedTime = remarkedAt.format(timeFormatter);
        }
        
        return String.format("By %s • %s – %s", authorLabel, formattedTime, preview);
    }

}
