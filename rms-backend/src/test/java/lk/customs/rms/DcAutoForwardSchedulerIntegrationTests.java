package lk.customs.rms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lk.customs.rms.entity.DcAutoForwardConfig;
import lk.customs.rms.entity.Document;
import lk.customs.rms.entity.Role;
import lk.customs.rms.entity.User;
import lk.customs.rms.enums.MovementActionType;
import lk.customs.rms.enums.Priority;
import lk.customs.rms.enums.Status;
import lk.customs.rms.repository.DcAutoForwardConfigRepository;
import lk.customs.rms.repository.DocumentMovementRepository;
import lk.customs.rms.repository.DocumentRepository;
import lk.customs.rms.repository.RoleRepository;
import lk.customs.rms.repository.UserRepository;
import lk.customs.rms.scheduler.DcAutoForwardScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
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
    private DcAutoForwardScheduler scheduler;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private WebApplicationContext webApplicationContext;

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

        enableAutoForward(receiver.getId(), 1);

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

        enableAutoForward(receiver.getId(), 1);

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

        enableAutoForward(receiver.getId(), 1);

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
                                  "priority": "HIGH"
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

    private void enableAutoForward(Long receiverUserId, int timeoutMinutes) {
        DcAutoForwardConfig config = dcAutoForwardConfigRepository.findById(1L).orElseGet(DcAutoForwardConfig::new);
        config.setId(1L);
        config.setEnabled(true);
        config.setReceiverUserId(receiverUserId);
        config.setTimeoutMinutes(timeoutMinutes);
        config.setForwardReturnAllowedStatuses("PENDING,IN_PROGRESS,RETURNED");
        config.setApproveRejectButtonsEnabled(true);
        dcAutoForwardConfigRepository.saveAndFlush(config);
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
