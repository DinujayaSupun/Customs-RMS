package lk.customs.rms;

import lk.customs.rms.entity.AuditLog;
import lk.customs.rms.repository.AuditLogRepository;
import lk.customs.rms.repository.DocumentAttachmentRepository;
import lk.customs.rms.repository.DocumentRepository;
import lk.customs.rms.repository.UserRepository;
import lk.customs.rms.service.impl.AuditLogServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditLogServiceImplTests {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AuditLogRepository auditLogRepository;
    private AuditLogServiceImpl auditLogService;

    @BeforeEach
    void setUp() {
        auditLogRepository = mock(AuditLogRepository.class);
        auditLogService = new AuditLogServiceImpl(
                auditLogRepository,
                objectMapper,
                mock(UserRepository.class),
                mock(DocumentRepository.class),
                mock(DocumentAttachmentRepository.class)
        );
    }

    @Test
    void logAttachmentStoresDetailsAsValidJson() throws Exception {
        auditLogService.logAttachment(10L, 55L, 7L, "UPLOAD", "Attachment uploaded");

        AuditLog saved = capturedAuditLog();
        JsonNode details = objectMapper.readTree(saved.getDetailsJson());

        assertThat(saved.getEntityType()).isEqualTo("ATTACHMENT");
        assertThat(saved.getEntityId()).isEqualTo(55L);
        assertThat(details.get("documentId").asLong()).isEqualTo(10L);
        assertThat(details.get("attachmentId").asLong()).isEqualTo(55L);
    }

    @Test
    void logRemarkStoresDetailsAsValidJson() throws Exception {
        auditLogService.logRemark(10L, 7L, "REMARK", "Minute added", 99L);

        AuditLog saved = capturedAuditLog();
        JsonNode details = objectMapper.readTree(saved.getDetailsJson());

        assertThat(saved.getEntityType()).isEqualTo("DOCUMENT");
        assertThat(saved.getEntityId()).isEqualTo(10L);
        assertThat(details.get("documentId").asLong()).isEqualTo(10L);
        assertThat(details.get("remarkId").asLong()).isEqualTo(99L);
    }

    @Test
    void logEventWithDetailsEscapesTextValuesSafely() throws Exception {
        auditLogService.logEventWithDetails(
                "USER",
                20L,
                "USER_IMPORT",
                7L,
                "Imported user",
                Map.of("fileName", "invoice \"final\".pdf")
        );

        AuditLog saved = capturedAuditLog();
        JsonNode details = objectMapper.readTree(saved.getDetailsJson());

        assertThat(details.get("fileName").asText()).isEqualTo("invoice \"final\".pdf");
        assertThat(saved.getDetailsJson()).contains("\\\"final\\\"");
    }

    @Test
    void exportCsvBytesNeutralizesFormulaInjectionInTextCells() {
        AuditLog log = new AuditLog();
        log.setId(1L);
        log.setEntityType("DOCUMENT");
        log.setEntityId(5L);
        log.setActionType("CREATE");
        log.setPerformedByUserId(null);
        log.setPerformedAt(LocalDateTime.of(2026, 1, 1, 10, 0, 0));
        log.setMessage("=SUM(1+1)*cmd");
        log.setDetailsJson(null);

        when(auditLogRepository.searchLogs(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(log)));

        String csv = new String(
                auditLogService.exportCsvBytes(null, null, null, null, null),
                StandardCharsets.UTF_8);

        // A cell starting with '=' would execute as a formula when opened in a spreadsheet, so the
        // export must prepend an apostrophe and never emit the raw leading '='.
        assertThat(csv).contains("'=SUM(1+1)*cmd");
        assertThat(csv).doesNotContain(",=SUM(1+1)*cmd");
    }

    private AuditLog capturedAuditLog() {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        return captor.getValue();
    }
}
