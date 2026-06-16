package lk.customs.rms.controller;

import lk.customs.rms.dto.AuditLogFilterOptionsResponse;
import lk.customs.rms.dto.AuditLogPerformerOptionResponse;
import lk.customs.rms.dto.AuditLogResponse;
import lk.customs.rms.entity.AuditLog;
import lk.customs.rms.entity.User;
import lk.customs.rms.enums.AppPermission;
import lk.customs.rms.exception.BadRequestException;
import lk.customs.rms.repository.AuditLogRepository;
import lk.customs.rms.repository.DocumentAttachmentRepository;
import lk.customs.rms.repository.DocumentRepository;
import lk.customs.rms.repository.UserRepository;
import lk.customs.rms.security.CurrentUserService;
import lk.customs.rms.service.PermissionService;
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
import org.springframework.security.core.Authentication;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@CrossOrigin
@RequestMapping("/api/audit-logs")
public class LogsController {
    private static final int MAX_EXPORT_ROWS = 10_000;

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final DocumentAttachmentRepository documentAttachmentRepository;
    private final CurrentUserService currentUserService;
    private final PermissionService permissionService;

    public LogsController(AuditLogRepository auditLogRepository,
                          UserRepository userRepository,
                          DocumentRepository documentRepository,
                          DocumentAttachmentRepository documentAttachmentRepository,
                          CurrentUserService currentUserService,
                          PermissionService permissionService) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.documentRepository = documentRepository;
        this.documentAttachmentRepository = documentAttachmentRepository;
        this.currentUserService = currentUserService;
        this.permissionService = permissionService;
    }

    @GetMapping
    public Page<AuditLogResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) Long performedByUserId,
            @RequestParam(required = false) String document,
            Authentication authentication
    ) {
        ensureCanViewLogs(authentication);
        Pageable pageable = PageRequest.of(page, size);

        LocalDateTime fromAt = fromDate == null ? null : fromDate.atStartOfDay();
        LocalDateTime toAtExclusive = toDate == null ? null : toDate.plusDays(1).atStartOfDay();

        String documentFilter = normalize(document);
        Long documentId = parseLongOrNull(documentFilter);

        // The document filter accepts either a text ref/title fragment or a numeric document id.
        Page<AuditLog> logs = auditLogRepository.searchLogs(
                fromAt,
                toAtExclusive,
                normalize(actionType),
                performedByUserId,
                documentFilter,
                documentId,
                pageable
        );
        Map<Long, User> usersById = usersByIdForLogs(logs.getContent());

        return logs.map(log -> toResponse(log, usersById));
    }

    @GetMapping("/filter-options")
    public AuditLogFilterOptionsResponse filterOptions(Authentication authentication) {
        ensureCanViewLogs(authentication);

        List<String> actionTypes = auditLogRepository.findDistinctActionTypes();

        List<Long> performerIds = auditLogRepository.findDistinctPerformedByUserIds();
        Map<Long, User> usersById = userRepository.findAllById(performerIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<AuditLogPerformerOptionResponse> performers = performerIds.stream()
                .map(userId -> {
                    User user = usersById.get(userId);
                    String name = user == null
                            ? "User ID " + userId
                            : firstNonBlank(user.getFullName(), user.getUsername(), "User ID " + userId);

                    return AuditLogPerformerOptionResponse.builder()
                            .id(userId)
                            .name(name)
                            .build();
                })
                .sorted(Comparator.comparing(AuditLogPerformerOptionResponse::getName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(AuditLogPerformerOptionResponse::getId))
                .toList();

        return AuditLogFilterOptionsResponse.builder()
                .actionTypes(actionTypes)
                .performers(performers)
                .build();
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) Long performedByUserId,
            @RequestParam(required = false) String document,
            Authentication authentication
    ) {
        ensureCanViewLogs(authentication);
        LocalDateTime fromAt = fromDate == null ? null : fromDate.atStartOfDay();
        LocalDateTime toAtExclusive = toDate == null ? null : toDate.plusDays(1).atStartOfDay();

        String documentFilter = normalize(document);
        Long documentId = parseLongOrNull(documentFilter);

        Page<AuditLog> exportPage = auditLogRepository.searchLogs(
                fromAt,
                toAtExclusive,
                normalize(actionType),
                performedByUserId,
                documentFilter,
                documentId,
                PageRequest.of(0, MAX_EXPORT_ROWS + 1)
        );
        if (exportPage.getTotalElements() > MAX_EXPORT_ROWS) {
            throw new BadRequestException("Export is too large. Narrow the date range or filters and try again.");
        }
        List<AuditLog> logs = exportPage.getContent();
        Map<Long, User> usersById = usersByIdForLogs(logs);

        StringBuilder csv = new StringBuilder();
        csv.append("id,performedAt,actionType,entityType,entityId,performedByUserId,performedByUserName,message,detailsJson\n");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        // Build CSV manually to keep export dependency-free and preserve the same filtered data as the grid.
        for (AuditLog log : logs) {
            String userName = userName(usersById, log.getPerformedByUserId());
            csv.append(log.getId()).append(',')
                    .append(csvCell(log.getPerformedAt() == null ? "" : formatter.format(log.getPerformedAt()))).append(',')
                    .append(csvCell(log.getActionType())).append(',')
                    .append(csvCell(log.getEntityType())).append(',')
                    .append(log.getEntityId() == null ? "" : log.getEntityId()).append(',')
                    .append(log.getPerformedByUserId() == null ? "" : log.getPerformedByUserId()).append(',')
                    .append(csvCell(userName)).append(',')
                    .append(csvCell(log.getMessage())).append(',')
                    .append(csvCell(log.getDetailsJson()))
                    .append('\n');
        }

        byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);
        String fileName = "audit-logs-" + LocalDate.now() + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(bytes);
    }

    private void ensureCanViewLogs(Authentication authentication) {
        Long actorUserId = currentUserService.requireUserId(authentication);
        if (!permissionService.hasPermission(actorUserId, AppPermission.VIEW_LOGS)) {
            throw new BadRequestException("You are not allowed to view logs.");
        }
    }

    private AuditLogResponse toResponse(AuditLog log, Map<Long, User> usersById) {
        return AuditLogResponse.builder()
                .id(log.getId())
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .documentRef(resolveDocumentRef(log))
                .actionType(log.getActionType())
                .performedByUserId(log.getPerformedByUserId())
                .performedByUserName(userName(usersById, log.getPerformedByUserId()))
                .performedAt(log.getPerformedAt())
                .message(log.getMessage())
                .detailsJson(log.getDetailsJson())
                .build();
    }

    private Map<Long, User> usersByIdForLogs(List<AuditLog> logs) {
        Set<Long> userIds = new HashSet<>();
        for (AuditLog log : logs) {
            if (log.getPerformedByUserId() != null) {
                userIds.add(log.getPerformedByUserId());
            }
        }

        if (userIds.isEmpty()) return Map.of();

        return userRepository.findAllById(userIds)
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private String userName(Map<Long, User> usersById, Long userId) {
        User user = userId == null ? null : usersById.get(userId);
        return user == null ? null : user.getFullName();
    }

    private String normalize(String value) {
        if (value == null) return null;
        String v = value.trim();
        return v.isEmpty() ? null : v;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
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
            // Older attachment logs may only have detailsJson, while newer ones can resolve through the attachment row.
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
