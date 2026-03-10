package lk.customs.rms.controller;

import lk.customs.rms.dto.AuditLogResponse;
import lk.customs.rms.entity.AuditLog;
import lk.customs.rms.repository.AuditLogRepository;
import lk.customs.rms.repository.DocumentAttachmentRepository;
import lk.customs.rms.repository.DocumentRepository;
import lk.customs.rms.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@CrossOrigin
@RequestMapping("/api/audit-logs")
@PreAuthorize("hasAnyRole('ADMIN','DC')")
public class LogsController {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final DocumentAttachmentRepository documentAttachmentRepository;

    public LogsController(AuditLogRepository auditLogRepository,
                          UserRepository userRepository,
                          DocumentRepository documentRepository,
                          DocumentAttachmentRepository documentAttachmentRepository) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.documentRepository = documentRepository;
        this.documentAttachmentRepository = documentAttachmentRepository;
    }

    @GetMapping
    public Page<AuditLogResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) Long performedByUserId,
            @RequestParam(required = false) String document
    ) {
        Pageable pageable = PageRequest.of(page, size);

        LocalDateTime fromAt = fromDate == null ? null : fromDate.atStartOfDay();
        LocalDateTime toAtExclusive = toDate == null ? null : toDate.plusDays(1).atStartOfDay();

        String documentFilter = normalize(document);
        Long documentId = parseLongOrNull(documentFilter);

        return auditLogRepository.searchLogs(
                fromAt,
                toAtExclusive,
                normalize(actionType),
                performedByUserId,
                documentFilter,
                documentId,
                pageable
        ).map(this::toResponse);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) Long performedByUserId,
            @RequestParam(required = false) String document
    ) {
        LocalDateTime fromAt = fromDate == null ? null : fromDate.atStartOfDay();
        LocalDateTime toAtExclusive = toDate == null ? null : toDate.plusDays(1).atStartOfDay();

        String documentFilter = normalize(document);
        Long documentId = parseLongOrNull(documentFilter);

        List<AuditLog> logs = auditLogRepository.exportLogs(
                fromAt,
                toAtExclusive,
                normalize(actionType),
                performedByUserId,
                documentFilter,
                documentId
        );

        StringBuilder csv = new StringBuilder();
        csv.append("id,performedAt,actionType,entityType,entityId,performedByUserId,performedByUserName,message\n");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        for (AuditLog log : logs) {
            String userName = userRepository.findById(log.getPerformedByUserId()).map(u -> u.getFullName()).orElse("");
            csv.append(log.getId()).append(',')
                    .append(csvCell(log.getPerformedAt() == null ? "" : formatter.format(log.getPerformedAt()))).append(',')
                    .append(csvCell(log.getActionType())).append(',')
                    .append(csvCell(log.getEntityType())).append(',')
                    .append(log.getEntityId() == null ? "" : log.getEntityId()).append(',')
                    .append(log.getPerformedByUserId() == null ? "" : log.getPerformedByUserId()).append(',')
                    .append(csvCell(userName)).append(',')
                    .append(csvCell(log.getMessage()))
                    .append('\n');
        }

        byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);
        String fileName = "audit-logs-" + LocalDate.now() + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(bytes);
    }

    private AuditLogResponse toResponse(AuditLog log) {
        return AuditLogResponse.builder()
                .id(log.getId())
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .documentRef(resolveDocumentRef(log))
                .actionType(log.getActionType())
                .performedByUserId(log.getPerformedByUserId())
                .performedByUserName(userRepository.findById(log.getPerformedByUserId()).map(u -> u.getFullName()).orElse(null))
                .performedAt(log.getPerformedAt())
                .message(log.getMessage())
                .detailsJson(log.getDetailsJson())
                .build();
    }

    private String normalize(String value) {
        if (value == null) return null;
        String v = value.trim();
        return v.isEmpty() ? null : v;
    }

    private Long parseLongOrNull(String value) {
        if (value == null) return null;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String csvCell(String value) {
        if (value == null) return "";
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\n") || escaped.contains("\"")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    private String resolveDocumentRef(AuditLog log) {
        Long documentId = resolveDocumentId(log);
        if (documentId == null) return null;
        return documentRepository.findByIdAndDeletedFalse(documentId).map(d -> d.getRefNo()).orElse(null);
    }

    private Long resolveDocumentId(AuditLog log) {
        String entityType = String.valueOf(log.getEntityType()).toUpperCase();
        if ("DOCUMENT".equals(entityType) || "MOVEMENT".equals(entityType)) {
            return log.getEntityId();
        }

        if ("ATTACHMENT".equals(entityType)) {
            Long fromDetails = extractDocumentIdFromDetails(log.getDetailsJson());
            if (fromDetails != null) return fromDetails;
            return documentAttachmentRepository.findByIdAndDeletedFalse(log.getEntityId())
                    .map(a -> a.getDocumentId())
                    .orElse(null);
        }

        return null;
    }

    private Long extractDocumentIdFromDetails(String detailsJson) {
        if (detailsJson == null || detailsJson.isBlank()) return null;
        String marker = "\"documentId\":";
        int idx = detailsJson.indexOf(marker);
        if (idx < 0) return null;

        int start = idx + marker.length();
        int end = start;
        while (end < detailsJson.length() && Character.isDigit(detailsJson.charAt(end))) {
            end++;
        }

        if (end <= start) return null;
        try {
            return Long.parseLong(detailsJson.substring(start, end));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
