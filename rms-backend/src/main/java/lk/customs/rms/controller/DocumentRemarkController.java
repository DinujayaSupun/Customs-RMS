package lk.customs.rms.controller;

import jakarta.validation.Valid;
import lk.customs.rms.dto.CreateRemarkRequest;
import lk.customs.rms.dto.RemarkResponse;
import lk.customs.rms.entity.Document;
import lk.customs.rms.entity.DocumentRemark;
import lk.customs.rms.exception.BadRequestException;
import lk.customs.rms.exception.ResourceNotFoundException;
import lk.customs.rms.repository.DocumentRemarkRepository;
import lk.customs.rms.repository.DocumentRepository;
import lk.customs.rms.repository.UserRepository;
import lk.customs.rms.security.CurrentUserService;
import lk.customs.rms.service.AuditLogService;
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
    private final DocumentRemarkRepository remarkRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
        private final CurrentUserService currentUserService;

    public DocumentRemarkController(
            DocumentRepository documentRepository,
            DocumentRemarkRepository remarkRepository,
            UserRepository userRepository,
                        AuditLogService auditLogService,
                        CurrentUserService currentUserService
    ) {
        this.documentRepository = documentRepository;
        this.remarkRepository = remarkRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
                this.currentUserService = currentUserService;
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

        auditLogService.logRemark(documentId, user.getId(), "REMARK", "Remark added by current owner", saved.getId());

        return RemarkResponse.builder()
                .id(saved.getId())
                .documentId(saved.getDocumentId())
                .remarkText(saved.getRemarkText())
                .remarkedByUserId(user.getId())
                .remarkedByName(user.getFullName())
                .remarkedAt(saved.getRemarkedAt())
                .build();
    }

    // ✅ LIST REMARKS FOR DOCUMENT
    @GetMapping
    public List<RemarkResponse> getRemarks(@PathVariable Long documentId) {

        documentRepository.findByIdAndDeletedFalse(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

        return remarkRepository.findByDocumentIdOrderByRemarkedAtAsc(documentId)
                .stream()
                .map(r -> RemarkResponse.builder()
                        .id(r.getId())
                        .documentId(r.getDocumentId())
                        .remarkText(r.getRemarkText())
                        .remarkedByUserId(r.getRemarkedByUserId())
                        .remarkedByName(r.getRemarkedBy() != null ? r.getRemarkedBy().getFullName() : null)
                        .remarkedAt(r.getRemarkedAt())
                        .build())
                .toList();
    }
}
