package lk.customs.rms;

import lk.customs.rms.controller.DocumentMovementController;
import lk.customs.rms.controller.LogsController;
import lk.customs.rms.dto.AuditLogResponse;
import lk.customs.rms.dto.MovementResponse;
import lk.customs.rms.entity.AuditLog;
import lk.customs.rms.entity.Document;
import lk.customs.rms.entity.DocumentMovement;
import lk.customs.rms.entity.User;
import lk.customs.rms.enums.AppPermission;
import lk.customs.rms.enums.MovementActionType;
import lk.customs.rms.repository.AuditLogRepository;
import lk.customs.rms.repository.DocumentAttachmentRepository;
import lk.customs.rms.repository.DocumentMovementRepository;
import lk.customs.rms.repository.DocumentRepository;
import lk.customs.rms.repository.UserRepository;
import lk.customs.rms.security.CurrentUserService;
import lk.customs.rms.service.DocumentRecipientService;
import lk.customs.rms.service.PermissionService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResponseBatchMappingTests {

    @Test
    void movementHistoryBatchesUserLookupsWhileMappingResponses() {
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        DocumentMovementRepository movementRepository = mock(DocumentMovementRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        PermissionService permissionService = mock(PermissionService.class);
        DocumentRecipientService documentRecipientService = mock(DocumentRecipientService.class);
        Authentication authentication = mock(Authentication.class);

        Document document = new Document();
        document.setId(99L);
        document.setCurrentOwnerUserId(10L);

        DocumentMovement first = movement(1L, 99L, null, 20L, 10L);
        DocumentMovement second = movement(2L, 99L, 20L, 30L, 10L);

        when(documentRepository.findByIdAndDeletedFalse(99L)).thenReturn(Optional.of(document));
        when(currentUserService.requireUserId(authentication)).thenReturn(10L);
        when(documentRecipientService.canViewTimeline(document, 10L)).thenReturn(true);
        when(movementRepository.findByDocumentIdOrderByActionAtAsc(99L)).thenReturn(List.of(first, second));
        when(userRepository.findAllById(any())).thenReturn(List.of(
                user(10L, "Owner User"),
                user(20L, "Middle User"),
                user(30L, "Target User")
        ));

        DocumentMovementController controller = new DocumentMovementController(
                documentRepository,
                movementRepository,
                userRepository,
                currentUserService,
                permissionService,
                documentRecipientService
        );

        List<MovementResponse> responses = controller.getMovements(99L, authentication);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getActionByUserName()).isEqualTo("Owner User");
        assertThat(responses.get(0).getToUserName()).isEqualTo("Middle User");
        assertThat(responses.get(1).getFromUserName()).isEqualTo("Middle User");
        assertThat(responses.get(1).getToUserName()).isEqualTo("Target User");
        verify(userRepository).findAllById(any());
        verify(userRepository, never()).findById(anyLong());
    }

    @Test
    void auditLogListBatchesPerformerLookupsWhileMappingResponses() {
        AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        DocumentAttachmentRepository attachmentRepository = mock(DocumentAttachmentRepository.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        PermissionService permissionService = mock(PermissionService.class);
        Authentication authentication = mock(Authentication.class);

        AuditLog first = auditLog(1L, 10L);
        AuditLog second = auditLog(2L, 20L);
        Page<AuditLog> page = new PageImpl<>(List.of(first, second));

        when(currentUserService.requireUserId(authentication)).thenReturn(7L);
        when(permissionService.hasPermission(7L, AppPermission.VIEW_LOGS)).thenReturn(true);
        when(auditLogRepository.searchLogs(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(Pageable.class)
        )).thenReturn(page);
        when(userRepository.findAllById(any())).thenReturn(List.of(
                user(10L, "First Performer"),
                user(20L, "Second Performer")
        ));

        LogsController controller = new LogsController(
                auditLogRepository,
                userRepository,
                documentRepository,
                attachmentRepository,
                currentUserService,
                permissionService
        );

        Page<AuditLogResponse> responses = controller.list(0, 20, null, null, null, null, null, authentication);

        assertThat(responses.getContent()).hasSize(2);
        assertThat(responses.getContent().get(0).getPerformedByUserName()).isEqualTo("First Performer");
        assertThat(responses.getContent().get(1).getPerformedByUserName()).isEqualTo("Second Performer");
        verify(userRepository).findAllById(any());
        verify(userRepository, never()).findById(anyLong());
    }

    @Test
    void auditLogExportBatchesPerformerLookupsWhileWritingCsv() {
        AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        DocumentAttachmentRepository attachmentRepository = mock(DocumentAttachmentRepository.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        PermissionService permissionService = mock(PermissionService.class);
        Authentication authentication = mock(Authentication.class);

        when(currentUserService.requireUserId(authentication)).thenReturn(7L);
        when(permissionService.hasPermission(7L, AppPermission.VIEW_LOGS)).thenReturn(true);
        when(auditLogRepository.searchLogs(any(), any(), any(), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of(
                auditLog(1L, 10L),
                auditLog(2L, 20L)
        )));
        when(userRepository.findAllById(any())).thenReturn(List.of(
                user(10L, "First Performer"),
                user(20L, "Second Performer")
        ));

        LogsController controller = new LogsController(
                auditLogRepository,
                userRepository,
                documentRepository,
                attachmentRepository,
                currentUserService,
                permissionService
        );

        String csv = new String(controller.exportCsv(null, null, null, null, null, authentication).getBody());

        assertThat(csv).contains("First Performer");
        assertThat(csv).contains("Second Performer");
        verify(userRepository).findAllById(any());
        verify(userRepository, never()).findById(anyLong());
    }

    @Test
    void auditLogExportIncludesDetailsJsonForInvestigationContext() {
        AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        DocumentAttachmentRepository attachmentRepository = mock(DocumentAttachmentRepository.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        PermissionService permissionService = mock(PermissionService.class);
        Authentication authentication = mock(Authentication.class);

        AuditLog deleteLog = auditLog(1L, 10L);
        deleteLog.setActionType("DELETE");
        deleteLog.setDetailsJson("{\"refNo\":\"CUS-001\",\"deletedByName\":\"Samantha\"}");

        when(currentUserService.requireUserId(authentication)).thenReturn(7L);
        when(permissionService.hasPermission(7L, AppPermission.VIEW_LOGS)).thenReturn(true);
        when(auditLogRepository.searchLogs(any(), any(), any(), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of(deleteLog)));
        when(userRepository.findAllById(any())).thenReturn(List.of(user(10L, "Samantha")));

        LogsController controller = new LogsController(
                auditLogRepository,
                userRepository,
                documentRepository,
                attachmentRepository,
                currentUserService,
                permissionService
        );

        String csv = new String(controller.exportCsv(null, null, null, null, null, authentication).getBody());

        assertThat(csv).contains("detailsJson");
        assertThat(csv).contains("\"{\"\"refNo\"\":\"\"CUS-001\"\",\"\"deletedByName\"\":\"\"Samantha\"\"}\"");
    }

    private DocumentMovement movement(Long id, Long documentId, Long fromUserId, Long toUserId, Long actionByUserId) {
        DocumentMovement movement = new DocumentMovement();
        movement.setId(id);
        movement.setDocumentId(documentId);
        movement.setFromUserId(fromUserId);
        movement.setToUserId(toUserId);
        movement.setActionByUserId(actionByUserId);
        movement.setActionType(MovementActionType.FORWARD);
        movement.setActionAt(LocalDateTime.now());
        return movement;
    }

    private AuditLog auditLog(Long id, Long performedByUserId) {
        AuditLog log = new AuditLog();
        log.setId(id);
        log.setEntityType("USER");
        log.setEntityId(performedByUserId);
        log.setActionType("USER_UPDATE");
        log.setPerformedByUserId(performedByUserId);
        log.setPerformedAt(LocalDateTime.now());
        log.setMessage("Updated user");
        return log;
    }

    private User user(Long id, String fullName) {
        User user = new User();
        user.setId(id);
        user.setFullName(fullName);
        user.setUsername("user-" + id);
        return user;
    }
}
