package lk.customs.rms.service.impl;

import lk.customs.rms.dto.AuditLogFilterOptionsResponse;
import lk.customs.rms.dto.AuditLogPerformerOptionResponse;
import lk.customs.rms.dto.AuditLogResponse;
import lk.customs.rms.entity.AuditLog;
import lk.customs.rms.entity.User;
import lk.customs.rms.exception.BadRequestException;
import lk.customs.rms.repository.AuditLogRepository;
import lk.customs.rms.repository.DocumentAttachmentRepository;
import lk.customs.rms.repository.DocumentRepository;
import lk.customs.rms.repository.UserRepository;
import lk.customs.rms.service.AuditLogService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

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

@Service
public class AuditLogServiceImpl implements AuditLogService {

    private static final int MAX_EXPORT_ROWS = 10_000;

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final DocumentAttachmentRepository documentAttachmentRepository;

    public AuditLogServiceImpl(AuditLogRepository auditLogRepository,
                               ObjectMapper objectMapper,
                               UserRepository userRepository,
                               DocumentRepository documentRepository,
                               DocumentAttachmentRepository documentAttachmentRepository) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.documentRepository = documentRepository;
        this.documentAttachmentRepository = documentAttachmentRepository;
    }

    // --- Write methods ---

    @Override
    public void logEvent(String entityType, Long entityId, String actionType,
                         Long userId, String message, String detailsJson) {
        save(entityType, entityId, actionType, userId, message, detailsJson);
    }

    @Override
    public void logEventWithDetails(String entityType, Long entityId, String actionType,
                                    Long userId, String message, Map<String, Object> details) {
        save(entityType, entityId, actionType, userId, message, toDetailsJson(details));
    }

    @Override
    public void logDocumentCreate(Long documentId, Long userId, String message) {
        save("DOCUMENT", documentId, "CREATE", userId, message, null);
    }

    @Override
    public void logDocumentUpdate(Long documentId, Long userId, String message) {
        save("DOCUMENT", documentId, "UPDATE", userId, message, null);
    }

    @Override
    public void logDocumentDelete(Long documentId, Long userId, String message) {
        save("DOCUMENT", documentId, "DELETE", userId, message, null);
    }

    @Override
    public void logMovement(Long documentId, Long userId, String actionType, String message) {
        save("MOVEMENT", documentId, actionType, userId, message, null);
    }

    @Override
    public void logAttachment(Long documentId, Long attachmentId, Long userId, String actionType, String message) {
        // Store documentId in details because the primary audit entity is the attachment row.
        String details = toDetailsJson(Map.of(
                "documentId", documentId,
                "attachmentId", attachmentId
        ));
        save("ATTACHMENT", attachmentId, actionType, userId, message, details);
    }

    @Override
    public void logRemark(Long documentId, Long userId, String actionType, String message, Long remarkId) {
        // Log as DOCUMENT so it is included when querying document history.
        String details = remarkId == null ? null : toDetailsJson(Map.of(
                "documentId", documentId,
                "remarkId", remarkId
        ));
        save("DOCUMENT", documentId, actionType, userId, message, details);
    }

    // --- Query methods ---

    @Override
    public Page<AuditLogResponse> searchLogs(LocalDate fromDate, LocalDate toDate,
                                             String actionType, Long performedByUserId,
                                             String document, int page, int size) {
        LocalDateTime fromAt = fromDate == null ? null : fromDate.atStartOfDay();
        LocalDateTime toAtExclusive = toDate == null ? null : toDate.plusDays(1).atStartOfDay();

        String documentFilter = normalize(document);
        Long documentId = parseLongOrNull(documentFilter);

        Page<AuditLog> logs = auditLogRepository.searchLogs(
                fromAt, toAtExclusive,
                normalize(actionType), performedByUserId,
                documentFilter, documentId,
                PageRequest.of(page, size)
        );
        Map<Long, User> usersById = usersByIdForLogs(logs.getContent());
        return logs.map(log -> toResponse(log, usersById));
    }

    @Override
    public AuditLogFilterOptionsResponse getFilterOptions() {
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
                    return AuditLogPerformerOptionResponse.builder().id(userId).name(name).build();
                })
                .sorted(Comparator.comparing(AuditLogPerformerOptionResponse::getName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(AuditLogPerformerOptionResponse::getId))
                .toList();

        return AuditLogFilterOptionsResponse.builder()
                .actionTypes(actionTypes)
                .performers(performers)
                .build();
    }

    @Override
    public byte[] exportCsvBytes(LocalDate fromDate, LocalDate toDate,
                                 String actionType, Long performedByUserId,
                                 String document) {
        LocalDateTime fromAt = fromDate == null ? null : fromDate.atStartOfDay();
        LocalDateTime toAtExclusive = toDate == null ? null : toDate.plusDays(1).atStartOfDay();

        String documentFilter = normalize(document);
        Long documentId = parseLongOrNull(documentFilter);

        Page<AuditLog> exportPage = auditLogRepository.searchLogs(
                fromAt, toAtExclusive,
                normalize(actionType), performedByUserId,
                documentFilter, documentId,
                PageRequest.of(0, MAX_EXPORT_ROWS + 1)
        );
        if (exportPage.getTotalElements() > MAX_EXPORT_ROWS) {
            throw new BadRequestException("Export is too large. Narrow the date range or filters and try again.");
        }

        List<AuditLog> logs = exportPage.getContent();
        Map<Long, User> usersById = usersByIdForLogs(logs);

        // Build CSV manually to keep export dependency-free and preserve the same filtered data as the grid.
        StringBuilder csv = new StringBuilder();
        csv.append("id,performedAt,actionType,entityType,entityId,performedByUserId,performedByUserName,message,detailsJson\n");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public List<AuditLogResponse> getHistoryForDocument(Long documentId) {
        String documentRef = documentRepository.findByIdAndDeletedFalse(documentId)
                .map(d -> d.getRefNo()).orElse(null);
        List<AuditLog> logs = auditLogRepository.findHistoryForDocument(documentId);
        Map<Long, User> usersById = usersByIdForLogs(logs);

        return logs.stream()
                .map(l -> AuditLogResponse.builder()
                        .id(l.getId())
                        .entityType(l.getEntityType())
                        .entityId(l.getEntityId())
                        .documentRef(documentRef)
                        .actionType(l.getActionType())
                        .performedByUserId(l.getPerformedByUserId())
                        .performedByUserName(userName(usersById, l.getPerformedByUserId()))
                        .performedAt(l.getPerformedAt())
                        .message(l.getMessage())
                        .detailsJson(l.getDetailsJson())
                        .build())
                .toList();
    }

    // --- Private helpers ---

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
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private String userName(Map<Long, User> usersById, Long userId) {
        User user = userId == null ? null : usersById.get(userId);
        return user == null ? null : user.getFullName();
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
        try {
            JsonNode node = objectMapper.readTree(detailsJson);
            JsonNode idNode = node.path("documentId");
            return idNode.isMissingNode() || idNode.isNull() ? null : idNode.asLong();
        } catch (JacksonException e) {
            return null;
        }
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

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return "";
    }

    private String csvCell(String value) {
        if (value == null) return "";
        String guarded = neutralizeCsvFormula(value);
        String escaped = guarded.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\n") || escaped.contains("\r") || escaped.contains("\"")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    // Prevent CSV formula injection: a cell starting with = + - @ (or tab/CR) can be executed as a
    // formula when the file is opened in a spreadsheet, so prefix it with an apostrophe.
    private String neutralizeCsvFormula(String value) {
        if (value.isEmpty()) return value;
        char first = value.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
            return "'" + value;
        }
        return value;
    }

    private String toDetailsJson(Map<String, Object> details) {
        if (details == null || details.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JacksonException ex) {
            throw new IllegalArgumentException("Could not serialize audit details.", ex);
        }
    }

    private void save(String entityType, Long entityId, String actionType,
                      Long userId, String message, String detailsJson) {
        AuditLog log = new AuditLog();
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setActionType(actionType);
        log.setPerformedByUserId(userId);
        log.setPerformedAt(LocalDateTime.now());
        log.setMessage(message);
        log.setDetailsJson(detailsJson);
        auditLogRepository.save(log);
    }
}
