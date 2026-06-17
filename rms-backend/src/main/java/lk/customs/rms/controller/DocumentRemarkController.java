package lk.customs.rms.controller;

import jakarta.validation.Valid;
import lk.customs.rms.dto.CreateRemarkRequest;
import lk.customs.rms.dto.RemarkResponse;
import lk.customs.rms.entity.Document;
import lk.customs.rms.entity.DocumentRemark;
import lk.customs.rms.enums.AppPermission;
import lk.customs.rms.enums.MovementActionType;
import lk.customs.rms.enums.Status;
import lk.customs.rms.exception.BadRequestException;
import lk.customs.rms.exception.ResourceNotFoundException;
import lk.customs.rms.repository.DocumentMovementRepository;
import lk.customs.rms.repository.DocumentRemarkRepository;
import lk.customs.rms.repository.DocumentRepository;
import lk.customs.rms.repository.UserRepository;
import lk.customs.rms.security.CurrentUserService;
import lk.customs.rms.service.AuditLogService;
import lk.customs.rms.service.DocumentRecipientService;
import lk.customs.rms.service.PermissionService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@CrossOrigin
@RequestMapping("/api/documents/{documentId}/remarks")
public class DocumentRemarkController {

    private final DocumentRepository documentRepository;
    private final DocumentMovementRepository movementRepository;
    private final DocumentRemarkRepository remarkRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
        private final CurrentUserService currentUserService;
        private final PermissionService permissionService;
        private final DocumentRecipientService documentRecipientService;

    public DocumentRemarkController(
            DocumentRepository documentRepository,
            DocumentMovementRepository movementRepository,
            DocumentRemarkRepository remarkRepository,
            UserRepository userRepository,
                        AuditLogService auditLogService,
                        CurrentUserService currentUserService,
                        PermissionService permissionService,
                        DocumentRecipientService documentRecipientService
    ) {
        this.documentRepository = documentRepository;
        this.movementRepository = movementRepository;
        this.remarkRepository = remarkRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
                this.currentUserService = currentUserService;
                this.permissionService = permissionService;
                this.documentRecipientService = documentRecipientService;
    }

    // ✅ ADD REMARK (ONLY CURRENT OWNER)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RemarkResponse addRemark(
            @PathVariable Long documentId,
            @Valid @RequestBody CreateRemarkRequest request,
            Authentication authentication
    ) {
        Document doc = documentRepository.findByIdAndDeletedFalse(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

        Long actorUserId = currentUserService.requireUserId(authentication);
        permissionService.ensurePermission(actorUserId, AppPermission.ADD_REMARK, "You are not allowed to add minutes.");

        // 🔒 Only current owner can add remark
        if (!doc.getCurrentOwnerUserId().equals(actorUserId)) {
            throw new BadRequestException("Only the current owner can add remarks.");
        }

        var user = userRepository.findById(actorUserId)
                .orElseThrow(() -> new BadRequestException("User not found: " + actorUserId));

        String text = request.getRemarkText() == null ? "" : request.getRemarkText().trim();
        if (text.isEmpty()) {
            throw new BadRequestException("Remark text cannot be empty.");
        }

        DocumentRemark remark = DocumentRemark.builder()
                .documentId(documentId)
                .remarkText(text)
                .remarkedByUserId(actorUserId)
                .remarkedAt(LocalDateTime.now())
                .build();

        DocumentRemark saved = remarkRepository.save(remark);
        doc.setUpdatedAt(LocalDateTime.now());
        documentRepository.save(doc);

        auditLogService.logRemark(documentId, user.getId(), "REMARK", "Remark added by current owner", saved.getId());

        return RemarkResponse.builder()
                .id(saved.getId())
                .documentId(saved.getDocumentId())
                .remarkText(saved.getRemarkText())
                .remarkedByUserId(user.getId())
                .remarkedByName(user.getFullName())
                .remarkedAt(saved.getRemarkedAt())
                .canEdit(canModifyRemark(doc, saved, actorUserId))
                .canDelete(canModifyRemark(doc, saved, actorUserId))
                .build();
    }

    @PutMapping("/{remarkId}")
    public RemarkResponse updateRemark(
            @PathVariable Long documentId,
            @PathVariable Long remarkId,
            @Valid @RequestBody CreateRemarkRequest request,
            Authentication authentication
    ) {
        Document doc = requireDocument(documentId);
        Long actorUserId = currentUserService.requireUserId(authentication);
        DocumentRemark remark = requireRemark(documentId, remarkId);
        ensureCanModifyRemark(doc, remark, actorUserId);

        String text = request.getRemarkText() == null ? "" : request.getRemarkText().trim();
        if (text.isEmpty()) {
            throw new BadRequestException("Remark text cannot be empty.");
        }

        remark.setRemarkText(text);
        DocumentRemark saved = remarkRepository.save(remark);
        doc.setUpdatedAt(LocalDateTime.now());
        documentRepository.save(doc);

        auditLogService.logRemark(documentId, actorUserId, "REMARK_UPDATE", "Minute updated by current owner", saved.getId());

        var user = userRepository.findById(actorUserId)
                .orElseThrow(() -> new BadRequestException("User not found: " + actorUserId));

        return RemarkResponse.builder()
                .id(saved.getId())
                .documentId(saved.getDocumentId())
                .remarkText(saved.getRemarkText())
                .remarkedByUserId(user.getId())
                .remarkedByName(user.getFullName())
                .remarkedAt(saved.getRemarkedAt())
                .canEdit(canModifyRemark(doc, saved, actorUserId))
                .canDelete(canModifyRemark(doc, saved, actorUserId))
                .build();
    }

    @DeleteMapping("/{remarkId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRemark(
            @PathVariable Long documentId,
            @PathVariable Long remarkId,
            Authentication authentication
    ) {
        Document doc = requireDocument(documentId);
        Long actorUserId = currentUserService.requireUserId(authentication);
        DocumentRemark remark = requireRemark(documentId, remarkId);
        ensureCanModifyRemark(doc, remark, actorUserId);

        remarkRepository.delete(remark);
        doc.setUpdatedAt(LocalDateTime.now());
        documentRepository.save(doc);

        auditLogService.logRemark(documentId, actorUserId, "REMARK_DELETE", "Minute deleted by current owner", remarkId);
    }

    // ✅ LIST REMARKS FOR DOCUMENT
    @GetMapping
    public List<RemarkResponse> getRemarks(@PathVariable Long documentId, Authentication authentication) {

        Document doc = requireDocument(documentId);

        Long actorUserId = currentUserService.requireUserId(authentication);
        if (!documentRecipientService.canViewMinutes(doc, actorUserId)) {
            throw new BadRequestException("You are not allowed to view minutes unless the document is assigned to you in Report At.");
        }

        return remarkRepository.findByDocumentIdOrderByRemarkedAtAsc(documentId)
                .stream()
                .map(r -> RemarkResponse.builder()
                        .id(r.getId())
                        .documentId(r.getDocumentId())
                        .remarkText(r.getRemarkText())
                        .remarkedByUserId(r.getRemarkedByUserId())
                        .remarkedByName(r.getRemarkedBy() != null ? r.getRemarkedBy().getFullName() : null)
                        .remarkedAt(r.getRemarkedAt())
                        .canEdit(canModifyRemark(doc, r, actorUserId))
                        .canDelete(canModifyRemark(doc, r, actorUserId))
                        .build())
                .toList();
    }

    private Document requireDocument(Long documentId) {
        return documentRepository.findByIdAndDeletedFalse(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));
    }

    private DocumentRemark requireRemark(Long documentId, Long remarkId) {
        DocumentRemark remark = remarkRepository.findById(remarkId)
                .orElseThrow(() -> new ResourceNotFoundException("Minute not found: " + remarkId));
        if (!documentId.equals(remark.getDocumentId())) {
            throw new ResourceNotFoundException("Minute not found: " + remarkId);
        }
        return remark;
    }

    private void ensureCanModifyRemark(Document doc, DocumentRemark remark, Long actorUserId) {
        if (!canModifyRemark(doc, remark, actorUserId)) {
            throw new BadRequestException("Only your latest minute in the current ownership period can be changed.");
        }
    }

    private boolean canModifyRemark(Document doc, DocumentRemark remark, Long actorUserId) {
        if (doc == null || remark == null || actorUserId == null) return false;
        if (Status.ISSUED.equals(doc.getStatus())) return false;
        if (!actorUserId.equals(doc.getCurrentOwnerUserId())) return false;
        if (!actorUserId.equals(remark.getRemarkedByUserId())) return false;
        if (movementRepository.existsByDocumentIdAndActionAtAfter(doc.getId(), remark.getRemarkedAt())) return false;

        LocalDateTime periodStartedAt = movementRepository
                .findFirstByDocumentIdAndToUserIdAndActionTypeInOrderByActionAtDescIdDesc(
                        doc.getId(),
                        actorUserId,
                        List.of(MovementActionType.CREATE, MovementActionType.FORWARD, MovementActionType.RETURN)
                )
                .map(m -> m.getActionAt())
                .orElse(LocalDateTime.MIN);

        if (remark.getRemarkedAt() == null || remark.getRemarkedAt().isBefore(periodStartedAt)) return false;

        return remarkRepository
                .findFirstByDocumentIdAndRemarkedByUserIdAndRemarkedAtGreaterThanEqualOrderByRemarkedAtDescIdDesc(
                        doc.getId(),
                        actorUserId,
                        periodStartedAt
                )
                .map(latest -> latest.getId().equals(remark.getId()))
                .orElse(false);
    }
}
