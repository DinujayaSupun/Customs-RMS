package lk.customs.rms.service.impl;

import lk.customs.rms.dto.*;
import lk.customs.rms.entity.Document;
import lk.customs.rms.entity.DocumentMovement;
import lk.customs.rms.entity.DocumentRemark;
import lk.customs.rms.entity.User;
import lk.customs.rms.enums.MovementActionType;
import lk.customs.rms.enums.Status;
import lk.customs.rms.exception.BadRequestException;
import lk.customs.rms.exception.ResourceNotFoundException;
import lk.customs.rms.repository.DocumentMovementRepository;
import lk.customs.rms.repository.DocumentRemarkRepository;
import lk.customs.rms.repository.DocumentRepository;
import lk.customs.rms.repository.UserRepository;
import lk.customs.rms.service.AuditLogService;
import lk.customs.rms.service.DocumentService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

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
 *   2) PMA restriction:
 *      - PMA can forward/return ONLY to DC.
 *
 *   3) DC-only decisions:
 *      - Only DC can APPROVE / REJECT / ISSUE.
 *      - DC must be current owner to do those actions.
 *
 *   4) FINAL STATE LOCK (critical):
 *      - Once ISSUED or REJECTED => NO further workflow actions allowed.
 *      - If APPROVED => ONLY ISSUE is allowed (no forward/return/approve/reject).
 *      - ISSUE allowed ONLY when status == APPROVED.
 *
 *   5) REOPEN (NEW):
 *      - Only DC can REOPEN an APPROVED or REJECTED document.
 *      - Not allowed if ISSUED (final).
 *      - Requires a reason (remarkText must not be empty).
 *      - Sets status back to IN_PROGRESS and clears completedAt.
 *      - Logs Movement(REOPEN) + Audit log + Remark.
 *
 *   6) Remarks:
 *      - Can be added standalone OR during workflow action.
 *      - Always saved BEFORE ownership changes.
 * ==========================================================
 */
@Service
public class DocumentServiceImpl implements DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentMovementRepository movementRepository;
    private final DocumentRemarkRepository remarkRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    public DocumentServiceImpl(
            DocumentRepository documentRepository,
            DocumentMovementRepository movementRepository,
            DocumentRemarkRepository remarkRepository,
            UserRepository userRepository,
            AuditLogService auditLogService
    ) {
        this.documentRepository = documentRepository;
        this.movementRepository = movementRepository;
        this.remarkRepository = remarkRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
    }

    // ==========================================================
    // DOCUMENT CRUD
    // ==========================================================

    @Override
    public DocumentResponse createDocument(CreateDocumentRequest request, Long actorUserId) {

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

        // Document starts as PENDING
        doc.setStatus(Status.PENDING);

        doc.setCreatedByUserId(createdBy.getId());
        doc.setCurrentOwnerUserId(createdBy.getId());
        doc.setCreatedAt(LocalDateTime.now());
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

        return DocumentResponse.from(saved, createdBy.getFullName(), owner.getFullName());
    }

    @Override
    public Page<DocumentResponse> getDocuments(int page, int size, String search) {
        var pageable = PageRequest.of(
            page,
            size,
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );

        Page<Document> docs;
        if (search == null || search.isBlank()) {
            docs = documentRepository.findAllNotDeleted(pageable);
        } else {
            docs = documentRepository.searchNotDeleted(search.trim(), pageable);
        }

        return docs.map(d -> {
            String createdByName = userRepository.findById(d.getCreatedByUserId()).map(User::getFullName).orElse(null);
            String ownerName = userRepository.findById(d.getCurrentOwnerUserId()).map(User::getFullName).orElse(null);
            return DocumentResponse.from(d, createdByName, ownerName);
        });
    }

    @Override
    public DocumentResponse getDocumentById(Long id) {
        Document d = requireDocument(id);

        String createdByName = userRepository.findById(d.getCreatedByUserId()).map(User::getFullName).orElse(null);
        String ownerName = userRepository.findById(d.getCurrentOwnerUserId()).map(User::getFullName).orElse(null);

        return DocumentResponse.from(d, createdByName, ownerName);
    }

    @Override
    public DocumentResponse updateDocument(Long id, UpdateDocumentRequest request, Long actorUserId) {
        Document d = requireDocument(id);

        // NOTE: We are not locking "update" by status in this version.
        // If Customs wants, we can block edits after APPROVED/ISSUED later.

        if (request.getTitle() != null) d.setTitle(request.getTitle());
        if (request.getCompanyName() != null) d.setCompanyName(request.getCompanyName());
        if (request.getReceivedDate() != null) d.setReceivedDate(request.getReceivedDate());
        if (request.getPriority() != null) d.setPriority(request.getPriority());

        Document saved = documentRepository.save(d);

        auditLogService.logDocumentUpdate(saved.getId(), actorUserId, "Document updated");

        String createdByName = userRepository.findById(saved.getCreatedByUserId()).map(User::getFullName).orElse(null);
        String ownerName = userRepository.findById(saved.getCurrentOwnerUserId()).map(User::getFullName).orElse(null);

        return DocumentResponse.from(saved, createdByName, ownerName);
    }

    @Override
    public void deleteDocument(Long id, Long actorUserId) {
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
    public void forward(Long documentId, ForwardReturnRequest request, Long actorUserId) {

        Document d = requireDocument(documentId);

        // FINAL STATE LOCK: cannot forward if already final / approved
        ensureCanForwardOrReturn(d);

        User actionBy = requireUser(actorUserId);
        User toUser = requireUser(request.getToUserId());

        // Ownership check
        if (!d.getCurrentOwnerUserId().equals(actionBy.getId())) {
            throw new BadRequestException("Only the current owner can forward this document.");
        }

        // PMA restriction
        if (isRole(actionBy, "PMA") && !isRole(toUser, "DC")) {
            throw new BadRequestException("PMA can forward ONLY to DC.");
        }

        // IMPORTANT: remark must be saved BEFORE ownership changes
        saveRemarkIfPresent(d, actionBy.getId(), request.getRemarkText(), "Remark added during forward");

        Long from = d.getCurrentOwnerUserId();
        Long to = toUser.getId();

        d.setCurrentOwnerUserId(to);
        d.setStatus(Status.IN_PROGRESS);
        documentRepository.save(d);

        DocumentMovement mv = DocumentMovement.create(documentId, from, to, actionBy.getId(), MovementActionType.FORWARD);
        movementRepository.save(mv);

        auditLogService.logMovement(documentId, actionBy.getId(), "FORWARD", "Forwarded to userId=" + to);
    }

    @Override
    public void returns(Long documentId, ForwardReturnRequest request, Long actorUserId) {

        Document d = requireDocument(documentId);

        // FINAL STATE LOCK
        ensureCanForwardOrReturn(d);

        User actionBy = requireUser(actorUserId);
        User toUser = requireUser(request.getToUserId());

        // Ownership check
        if (!d.getCurrentOwnerUserId().equals(actionBy.getId())) {
            throw new BadRequestException("Only the current owner can return this document.");
        }

        // PMA restriction
        if (isRole(actionBy, "PMA") && !isRole(toUser, "DC")) {
            throw new BadRequestException("PMA can return ONLY to DC.");
        }

        // Save remark BEFORE ownership change
        saveRemarkIfPresent(d, actionBy.getId(), request.getRemarkText(), "Remark added during return");

        Long from = d.getCurrentOwnerUserId();
        Long to = toUser.getId();

        d.setCurrentOwnerUserId(to);
        d.setStatus(Status.RETURNED);
        documentRepository.save(d);

        DocumentMovement mv = DocumentMovement.create(documentId, from, to, actionBy.getId(), MovementActionType.RETURN);
        movementRepository.save(mv);

        auditLogService.logMovement(documentId, actionBy.getId(), "RETURN", "Returned to userId=" + to);
    }

    @Override
    public void approve(Long documentId, DecisionRequest request, Long actorUserId) {

        // DC only
        requireDc(actorUserId);

        Document d = requireDocument(documentId);

        // FINAL STATE + transition guard
        ensureCanApproveOrReject(d);

        // DC must be current owner
        if (!d.getCurrentOwnerUserId().equals(actorUserId)) {
            throw new BadRequestException("Only the current owner (DC) can approve this document.");
        }

        saveRemarkIfPresent(d, actorUserId, request.getRemarkText(), "Remark added during approve");

        d.setStatus(Status.APPROVED);
        d.setCompletedAt(LocalDateTime.now());
        documentRepository.save(d);

        DocumentMovement mv = DocumentMovement.create(documentId, d.getCurrentOwnerUserId(), null, actorUserId, MovementActionType.APPROVE);
        movementRepository.save(mv);

        auditLogService.logMovement(documentId, actorUserId, "APPROVE", "Approved by DC");
    }

    @Override
    public void reject(Long documentId, DecisionRequest request, Long actorUserId) {

        // DC only
        requireDc(actorUserId);

        Document d = requireDocument(documentId);

        // FINAL STATE + transition guard
        ensureCanApproveOrReject(d);

        if (!d.getCurrentOwnerUserId().equals(actorUserId)) {
            throw new BadRequestException("Only the current owner (DC) can reject this document.");
        }

        saveRemarkIfPresent(d, actorUserId, request.getRemarkText(), "Remark added during reject");

        d.setStatus(Status.REJECTED);
        d.setCompletedAt(LocalDateTime.now());
        documentRepository.save(d);

        DocumentMovement mv = DocumentMovement.create(documentId, d.getCurrentOwnerUserId(), null, actorUserId, MovementActionType.REJECT);
        movementRepository.save(mv);

        auditLogService.logMovement(documentId, actorUserId, "REJECT", "Rejected by DC");
    }

    @Override
    public void issue(Long documentId, DecisionRequest request, Long actorUserId) {

        // DC only
        requireDc(actorUserId);

        Document d = requireDocument(documentId);

        // ISSUE must happen ONLY after APPROVED
        ensureCanIssue(d);

        if (!d.getCurrentOwnerUserId().equals(actorUserId)) {
            throw new BadRequestException("Only the current owner (DC) can issue this document.");
        }

        saveRemarkIfPresent(d, actorUserId, request.getRemarkText(), "Remark added during issue");

        d.setStatus(Status.ISSUED);
        d.setIssuedAt(LocalDateTime.now());
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

        // DC only
        requireDc(actorUserId);

        Document d = requireDocument(documentId);

        // ISSUED is final - cannot reopen
        if (d.getStatus() == Status.ISSUED) {
            throw new BadRequestException("Cannot reopen an ISSUED document.");
        }

        // Only APPROVED or REJECTED can be reopened
        if (d.getStatus() != Status.APPROVED && d.getStatus() != Status.REJECTED) {
            throw new BadRequestException("Reopen is allowed only for APPROVED or REJECTED documents.");
        }

        // DC must also be the current owner (strong integrity)
        if (!d.getCurrentOwnerUserId().equals(actorUserId)) {
            throw new BadRequestException("Only the current owner (DC) can reopen this document.");
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
        if (d.getStatus() == Status.ISSUED) {
            throw new BadRequestException("Cannot forward/return an ISSUED document.");
        }
        if (d.getStatus() == Status.REJECTED) {
            throw new BadRequestException("Cannot forward/return a REJECTED document.");
        }
        if (d.getStatus() == Status.APPROVED) {
            throw new BadRequestException("Cannot forward/return an APPROVED document. Only ISSUE is allowed.");
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

    // ==========================================================
    // REMARK HELPER
    // ==========================================================

    private void saveRemarkIfPresent(Document doc, Long actionByUserId, String remarkText, String auditMessage) {
        if (remarkText == null) return;

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

        auditLogService.logRemark(doc.getId(), actionByUserId, "REMARK", auditMessage, saved.getId());
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

    private void requireDc(Long userId) {
        User user = requireUser(userId);
        if (user.getRole() == null || !"DC".equalsIgnoreCase(user.getRole().getRoleName())) {
            throw new BadRequestException("Only DC can perform this action.");
        }
    }
}
