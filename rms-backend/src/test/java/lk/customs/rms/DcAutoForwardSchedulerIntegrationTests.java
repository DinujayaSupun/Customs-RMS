package lk.customs.rms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lk.customs.rms.dto.RealtimeNotificationMessage;
import lk.customs.rms.entity.DcAutoForwardConfig;
import lk.customs.rms.entity.DcAutoForwardReceiver;
import lk.customs.rms.entity.Document;
import lk.customs.rms.entity.Role;
import lk.customs.rms.entity.User;
import lk.customs.rms.enums.MovementActionType;
import lk.customs.rms.enums.Priority;
import lk.customs.rms.enums.Status;
import lk.customs.rms.entity.RecipientGroup;
import lk.customs.rms.entity.RecipientGroupMember;
import lk.customs.rms.repository.DcAutoForwardConfigRepository;
import lk.customs.rms.repository.DcAutoForwardReceiverRepository;
import lk.customs.rms.repository.DocumentMovementRepository;
import lk.customs.rms.repository.DocumentRepository;
import lk.customs.rms.repository.RecipientGroupMemberRepository;
import lk.customs.rms.repository.RecipientGroupRepository;
import lk.customs.rms.repository.RoleRepository;
import lk.customs.rms.repository.UserRepository;
import lk.customs.rms.scheduler.DcAutoForwardScheduler;
import lk.customs.rms.websocket.NotificationWebSocketHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class DcAutoForwardSchedulerIntegrationTests {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentMovementRepository movementRepository;

    @Autowired
    private DcAutoForwardConfigRepository dcAutoForwardConfigRepository;

    @Autowired
    private DcAutoForwardReceiverRepository dcAutoForwardReceiverRepository;

    @Autowired
    private RecipientGroupRepository recipientGroupRepository;

    @Autowired
    private RecipientGroupMemberRepository recipientGroupMemberRepository;

    @Autowired
    private DcAutoForwardScheduler scheduler;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @MockitoSpyBean
    private NotificationWebSocketHandler notificationWebSocketHandler;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        resetAutoForwardConfig();
    }

    @Test
    void timedOutUnopenedDcDocumentAutoForwardsToConfiguredDdcReceiver() throws Exception {
        String password = "AutoForward123";
        User creator = createUser("ADMIN", "auto-creator-", password);
        User dc = createUser("DC", "auto-dc-", password);
        User receiver = createUser("DDC", "auto-receiver-", password);

        String creatorToken = loginAndGetToken(creator.getUsername(), password);
        long documentId = createDocument(creatorToken, "auto-forward");

        mockMvc.perform(post("/api/documents/{id}/forward", documentId)
                        .header("Authorization", bearer(creatorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "toUserId": %d,
                                  "forwardVisibility": "PUBLIC",
                                  "remarkText": "Assign to DC for timeout test"
                                }
                                """.formatted(dc.getId())))
                .andExpect(status().isOk());

        Document assigned = requireDocument(documentId);
        assigned.setDcAssignedAt(LocalDateTime.now().minusMinutes(10));
        assigned.setDcViewedAt(null);
        documentRepository.saveAndFlush(assigned);

        enableAutoForward(dc.getId(), receiver.getId(), 1);

        scheduler.processTimedOutDcDocuments();

        Document forwarded = requireDocument(documentId);
        assertThat(forwarded.getCurrentOwnerUserId()).isEqualTo(receiver.getId());
        assertThat(forwarded.getStatus()).isEqualTo(Status.IN_PROGRESS);
        assertThat(forwarded.getDcAssignedAt()).isNull();
        assertThat(forwarded.getDcViewedAt()).isNull();

        assertThat(movementRepository.findByDocumentIdOrderByActionAtAsc(documentId))
                .anySatisfy(movement -> {
                    assertThat(movement.getActionType()).isEqualTo(MovementActionType.FORWARD);
                    assertThat(movement.getFromUserId()).isEqualTo(dc.getId());
                    assertThat(movement.getToUserId()).isEqualTo(receiver.getId());
                    assertThat(movement.getActionByUserId()).isEqualTo(dc.getId());
                });
    }

    @Test
    void viewedDcDocumentIsNotAutoForwardedAfterTimeout() throws Exception {
        String password = "AutoForward123";
        User creator = createUser("ADMIN", "auto-viewed-creator-", password);
        User dc = createUser("DC", "auto-viewed-dc-", password);
        User receiver = createUser("DDC", "auto-viewed-receiver-", password);

        String creatorToken = loginAndGetToken(creator.getUsername(), password);
        String dcToken = loginAndGetToken(dc.getUsername(), password);
        long documentId = createDocument(creatorToken, "auto-viewed");

        mockMvc.perform(post("/api/documents/{id}/forward", documentId)
                        .header("Authorization", bearer(creatorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "toUserId": %d,
                                  "forwardVisibility": "PUBLIC",
                                  "remarkText": "Assign to DC before view"
                                }
                                """.formatted(dc.getId())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/documents/{id}", documentId)
                        .header("Authorization", bearer(dcToken)))
                .andExpect(status().isOk());

        Document viewed = requireDocument(documentId);
        viewed.setDcAssignedAt(LocalDateTime.now().minusMinutes(10));
        documentRepository.saveAndFlush(viewed);

        enableAutoForward(dc.getId(), receiver.getId(), 1);

        scheduler.processTimedOutDcDocuments();

        Document unchanged = requireDocument(documentId);
        assertThat(unchanged.getCurrentOwnerUserId()).isEqualTo(dc.getId());
        assertThat(unchanged.getDcViewedAt()).isNotNull();
    }

    @Test
    void schedulerProcessesTimedOutDcDocumentsInBoundedBatchesWithoutSkipping() {
        String password = "AutoForward123";
        User creator = createUser("ADMIN", "auto-batch-creator-", password);
        User dc = createUser("DC", "auto-batch-dc-", password);
        User receiver = createUser("DDC", "auto-batch-receiver-", password);

        int totalCandidates = 1005;
        for (int index = 0; index < totalCandidates; index += 1) {
            createTimedOutDcDocument(creator.getId(), dc.getId(), "auto-batch-" + index);
        }

        enableAutoForward(dc.getId(), receiver.getId(), 1);

        scheduler.processTimedOutDcDocuments();

        long forwardedAfterFirstRun = documentRepository.findAll().stream()
                .filter(document -> document.getRefNo().startsWith("auto-batch-"))
                .filter(document -> receiver.getId().equals(document.getCurrentOwnerUserId()))
                .count();
        assertThat(forwardedAfterFirstRun).isEqualTo(1000);

        scheduler.processTimedOutDcDocuments();

        long forwardedAfterSecondRun = documentRepository.findAll().stream()
                .filter(document -> document.getRefNo().startsWith("auto-batch-"))
                .filter(document -> receiver.getId().equals(document.getCurrentOwnerUserId()))
                .count();
        assertThat(forwardedAfterSecondRun).isEqualTo(totalCandidates);
    }

    @Test
    void eachDcAutoForwardsToItsOwnMappedReceiverAndUnmappedDcIsSkipped() {
        String password = "AutoForward123";
        User creator = createUser("ADMIN", "auto-perdc-creator-", password);
        User dcA = createUser("DC", "auto-perdc-dcA-", password);
        User dcB = createUser("DC", "auto-perdc-dcB-", password);
        User dcNoMapping = createUser("DC", "auto-perdc-dcNone-", password);
        User receiverA = createUser("DDC", "auto-perdc-receiverA-", password);
        User receiverB = createUser("SDDC", "auto-perdc-receiverB-", password);

        Document docA = createTimedOutDcDocument(creator.getId(), dcA.getId(), "auto-perdc-a");
        Document docB = createTimedOutDcDocument(creator.getId(), dcB.getId(), "auto-perdc-b");
        Document docNoMapping = createTimedOutDcDocument(creator.getId(), dcNoMapping.getId(), "auto-perdc-none");

        // Enable, then map dcA -> receiverA and dcB -> receiverB. dcNoMapping is left unmapped.
        enableAutoForward(dcA.getId(), receiverA.getId(), 1);
        setDcReceiverMapping(dcB.getId(), receiverB.getId());

        scheduler.processTimedOutDcDocuments();

        assertThat(requireDocument(docA.getId()).getCurrentOwnerUserId()).isEqualTo(receiverA.getId());
        assertThat(requireDocument(docB.getId()).getCurrentOwnerUserId()).isEqualTo(receiverB.getId());
        // No mapping configured for this DC -> its document is left untouched, still with the DC.
        assertThat(requireDocument(docNoMapping.getId()).getCurrentOwnerUserId()).isEqualTo(dcNoMapping.getId());
    }

    @Test
    void autoForwardSendsARealtimePushToTheReceiverNotJustASilentDbUpdate() {
        String password = "AutoForward123";
        User creator = createUser("ADMIN", "auto-notify-creator-", password);
        User dc = createUser("DC", "auto-notify-dc-", password);
        User receiver = createUser("DDC", "auto-notify-receiver-", password);

        createTimedOutDcDocument(creator.getId(), dc.getId(), "auto-notify");
        enableAutoForward(dc.getId(), receiver.getId(), 1);

        Mockito.clearInvocations(notificationWebSocketHandler);
        scheduler.processTimedOutDcDocuments();

        ArgumentCaptor<Long> userIdCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<RealtimeNotificationMessage> messageCaptor = ArgumentCaptor.forClass(RealtimeNotificationMessage.class);
        Mockito.verify(notificationWebSocketHandler, Mockito.atLeastOnce())
                .sendToUser(userIdCaptor.capture(), messageCaptor.capture());

        String typeSentToReceiver = null;
        for (int i = 0; i < userIdCaptor.getAllValues().size(); i++) {
            if (receiver.getId().equals(userIdCaptor.getAllValues().get(i))) {
                typeSentToReceiver = messageCaptor.getAllValues().get(i).type();
            }
        }
        assertThat(typeSentToReceiver)
                .as("the receiver must get a real-time push so the document appears without a manual refresh")
                .isEqualTo("DOCUMENT_FORWARDED");
    }

    @Test
    void autoForwardingAGroupHeldDcDocumentClearsGroupHeldStateSoTheOtherGroupAdminCanNoLongerAct() throws Exception {
        String password = "AutoForward123";
        User creator = createUser("ADMIN", "auto-grp-creator-", password);
        User dc = createUser("DC", "auto-grp-dc-", password);
        User otherGroupAdmin = createUser("SC", "auto-grp-other-", password);
        User receiver = createUser("DDC", "auto-grp-receiver-", password);
        String otherGroupAdminToken = loginAndGetToken(otherGroupAdmin.getUsername(), password);

        long groupId = createGroupWithAdmins("Auto-Forward Test Group", creator.getId(), dc.getId(), otherGroupAdmin.getId());
        Document document = createTimedOutDcDocument(creator.getId(), dc.getId(), "auto-grp");
        document.setCurrentOwnerGroupId(groupId);
        documentRepository.saveAndFlush(document);

        enableAutoForward(dc.getId(), receiver.getId(), 1);

        scheduler.processTimedOutDcDocuments();

        Document forwarded = requireDocument(document.getId());
        assertThat(forwarded.getCurrentOwnerUserId()).isEqualTo(receiver.getId());
        assertThat(forwarded.getCurrentOwnerGroupId())
                .as("auto-forward clears group-held state, not just the owning user")
                .isNull();

        // Before the fix, currentOwnerGroupId stayed set and canActOnDocument() kept treating any
        // admin of the timed-out DC's group as a co-owner of a document that now belongs to receiver.
        mockMvc.perform(post("/api/documents/{id}/forward", document.getId())
                        .header("Authorization", bearer(otherGroupAdminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "toUserId": %d, "forwardVisibility": "PUBLIC" }
                                """.formatted(creator.getId())))
                .andExpect(status().isBadRequest());
    }

    private long createGroupWithAdmins(String name, Long createdByUserId, Long... adminUserIds) {
        RecipientGroup group = new RecipientGroup();
        group.setName(name);
        group.setColor("#123456");
        group.setCreatedByUserId(createdByUserId);
        group.setCreatedAt(LocalDateTime.now());
        group = recipientGroupRepository.saveAndFlush(group);
        for (Long adminUserId : adminUserIds) {
            RecipientGroupMember member = new RecipientGroupMember();
            member.setGroupId(group.getId());
            member.setUserId(adminUserId);
            member.setIsAdmin(true);
            recipientGroupMemberRepository.saveAndFlush(member);
        }
        return group.getId();
    }

    private User createUser(String roleName, String prefix, String rawPassword) {
        Role role = roleRepository.findByRoleName(roleName)
                .orElseThrow(() -> new IllegalStateException("Role not found: " + roleName));

        User user = new User();
        user.setFullName(prefix + "user");
        user.setUsername(prefix + UUID.randomUUID());
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        user.setIsActive(true);
        return userRepository.saveAndFlush(user);
    }

    private long createDocument(String token, String refPrefix) throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/documents")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refNo": "%s-%s",
                                  "title": "Auto Forward Test Document",
                                  "receivedDate": "%s",
                                  "companyName": "Integration Co",
                                  "priority": "HIGH",
                                  "documentType": "INTERNAL"
                                }
                                """.formatted(refPrefix, UUID.randomUUID(), LocalDate.now())))
                .andExpect(status().isCreated())
                .andReturn();

        return readJson(createResult).get("id").asLong();
    }

    private Document createTimedOutDcDocument(Long creatorUserId, Long dcUserId, String refPrefix) {
        Document document = new Document();
        document.setRefNo(refPrefix + "-" + UUID.randomUUID());
        document.setTitle("Auto Forward Batch Test Document");
        document.setReceivedDate(LocalDate.now());
        document.setCompanyName("Integration Co");
        document.setVisibility("PUBLIC");
        document.setPriority(Priority.HIGH);
        document.setStatus(Status.IN_PROGRESS);
        document.setCreatedByUserId(creatorUserId);
        document.setCurrentOwnerUserId(dcUserId);
        document.setCreatedAt(LocalDateTime.now().minusDays(1));
        document.setUpdatedAt(LocalDateTime.now().minusMinutes(20));
        document.setDcAssignedAt(LocalDateTime.now().minusMinutes(10));
        document.setDcViewedAt(null);
        return documentRepository.saveAndFlush(document);
    }

    /** Enables auto-forward and maps every id in dcUserIds to the same receiver (the common single-DC test shape). */
    private void enableAutoForward(Long dcUserId, Long receiverUserId, int timeoutMinutes) {
        DcAutoForwardConfig config = dcAutoForwardConfigRepository.findById(1L).orElseGet(DcAutoForwardConfig::new);
        config.setId(1L);
        config.setEnabled(true);
        config.setTimeoutMinutes(timeoutMinutes);
        config.setForwardReturnAllowedStatuses("PENDING,IN_PROGRESS,RETURNED");
        config.setApproveRejectButtonsEnabled(true);
        dcAutoForwardConfigRepository.saveAndFlush(config);
        setDcReceiverMapping(dcUserId, receiverUserId);
    }

    private void setDcReceiverMapping(Long dcUserId, Long receiverUserId) {
        DcAutoForwardReceiver mapping = dcAutoForwardReceiverRepository.findByDcUserId(dcUserId)
                .orElseGet(DcAutoForwardReceiver::new);
        mapping.setDcUserId(dcUserId);
        mapping.setReceiverUserId(receiverUserId);
        dcAutoForwardReceiverRepository.saveAndFlush(mapping);
    }

    private void resetAutoForwardConfig() {
        DcAutoForwardConfig config = dcAutoForwardConfigRepository.findById(1L).orElseGet(DcAutoForwardConfig::new);
        config.setId(1L);
        config.setEnabled(false);
        config.setReceiverUserId(null);
        config.setTimeoutMinutes(60);
        config.setForwardReturnAllowedStatuses("PENDING,IN_PROGRESS,RETURNED");
        config.setApproveRejectButtonsEnabled(true);
        dcAutoForwardConfigRepository.saveAndFlush(config);
        dcAutoForwardReceiverRepository.deleteAll();
    }

    private Document requireDocument(long documentId) {
        return documentRepository.findByIdAndDeletedFalse(documentId)
                .orElseThrow(() -> new IllegalStateException("Document not found: " + documentId));
    }

    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String loginAndGetToken(String username, String password) throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "%s"
                                }
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn();

        return readJson(loginResult).get("accessToken").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
