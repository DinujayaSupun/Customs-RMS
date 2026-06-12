package lk.customs.rms;

import lk.customs.rms.entity.AuditLog;
import lk.customs.rms.repository.AuditLogRepository;
import lk.customs.rms.service.impl.AuditLogServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuditLogServiceImplTests {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AuditLogRepository auditLogRepository;
    private AuditLogServiceImpl auditLogService;

    @BeforeEach
    void setUp() {
        auditLogRepository = mock(AuditLogRepository.class);
        auditLogService = new AuditLogServiceImpl(auditLogRepository, objectMapper);
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

    private AuditLog capturedAuditLog() {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        return captor.getValue();
    }
}
