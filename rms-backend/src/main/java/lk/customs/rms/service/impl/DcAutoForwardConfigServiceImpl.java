package lk.customs.rms.service.impl;

import lk.customs.rms.dto.DcAutoForwardConfigResponse;
import lk.customs.rms.dto.DcAutoForwardReceiverEntry;
import lk.customs.rms.dto.UpdateDcAutoForwardConfigRequest;
import lk.customs.rms.entity.DcAutoForwardConfig;
import lk.customs.rms.entity.DcAutoForwardReceiver;
import lk.customs.rms.entity.User;
import lk.customs.rms.enums.MovementActionType;
import lk.customs.rms.enums.Status;
import lk.customs.rms.exception.BadRequestException;
import lk.customs.rms.repository.DcAutoForwardConfigRepository;
import lk.customs.rms.repository.DcAutoForwardReceiverRepository;
import lk.customs.rms.repository.UserRepository;
import lk.customs.rms.service.DcAutoForwardConfigService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class DcAutoForwardConfigServiceImpl implements DcAutoForwardConfigService {

    // Workflow settings are global for the installation, so they are stored as a singleton row.
    private static final Long SINGLETON_ID = 1L;
    private static final List<Status> DEFAULT_FORWARD_RETURN_ALLOWED_STATUSES =
            List.of(Status.PENDING, Status.IN_PROGRESS, Status.RETURNED);
    private static final List<MovementActionType> DEFAULT_UNDO_SEND_ALLOWED_ACTIONS =
            List.of(MovementActionType.FORWARD, MovementActionType.RETURN);

    private final DcAutoForwardConfigRepository configRepository;
    private final UserRepository userRepository;
    private final DcAutoForwardReceiverRepository dcReceiverRepository;

    public DcAutoForwardConfigServiceImpl(DcAutoForwardConfigRepository configRepository,
                                          UserRepository userRepository,
                                          DcAutoForwardReceiverRepository dcReceiverRepository) {
        this.configRepository = configRepository;
        this.userRepository = userRepository;
        this.dcReceiverRepository = dcReceiverRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public DcAutoForwardConfigResponse getConfig() {
        return toResponse(getOrCreateEntity());
    }

    @Override
    @Transactional
    public DcAutoForwardConfigResponse updateConfig(UpdateDcAutoForwardConfigRequest request) {
        DcAutoForwardConfig config = getOrCreateEntity();

        config.setEnabled(Boolean.TRUE.equals(request.getEnabled()));
        config.setTimeoutMinutes(request.getTimeoutMinutes());
        config.setApproveRejectButtonsEnabled(Boolean.TRUE.equals(request.getApproveRejectButtonsEnabled()));
        if (request.getUndoSendEnabled() != null) {
            config.setUndoSendEnabled(Boolean.TRUE.equals(request.getUndoSendEnabled()));
        }
        if (request.getUndoSendWindowHours() != null) {
            config.setUndoSendWindowHours(request.getUndoSendWindowHours());
        }
        if (request.getUndoSendRequiresUnopened() != null) {
            config.setUndoSendRequiresUnopened(Boolean.TRUE.equals(request.getUndoSendRequiresUnopened()));
        }
        if (request.getUndoSendRequiresReason() != null) {
            config.setUndoSendRequiresReason(Boolean.TRUE.equals(request.getUndoSendRequiresReason()));
        }
        if (request.getUndoSendNotifyReceiver() != null) {
            config.setUndoSendNotifyReceiver(Boolean.TRUE.equals(request.getUndoSendNotifyReceiver()));
        }
        if (request.getUndoSendShowExpiredInfo() != null) {
            config.setUndoSendShowExpiredInfo(Boolean.TRUE.equals(request.getUndoSendShowExpiredInfo()));
        }

        // Kept for backward compatibility. Receiver assignment is now per-DC (below); this
        // singleton value is no longer required and no longer read by the scheduler.
        Long receiverUserId = request.getReceiverUserId();
        if (receiverUserId == null) {
            config.setReceiverUserId(null);
        } else {
            User receiver = requireReceiver(receiverUserId);
            config.setReceiverUserId(receiver.getId());
        }

        if (request.getDcReceivers() != null) {
            if (config.isEnabled() && request.getDcReceivers().isEmpty()) {
                throw new BadRequestException("Configure at least one DC receiver mapping when DC auto-forward is enabled.");
            }
            saveDcReceiverMappings(request.getDcReceivers());
        }

        if (request.getForwardReturnAllowedStatuses() != null) {
            List<Status> allowedStatuses = normalizeStatuses(request.getForwardReturnAllowedStatuses());
            config.setForwardReturnAllowedStatuses(toCsv(allowedStatuses));
        }
        if (request.getUndoSendAllowedActions() != null) {
            config.setUndoSendAllowedActions(toActionCsv(normalizeUndoActions(request.getUndoSendAllowedActions())));
        }

        config.setUpdatedAt(LocalDateTime.now());
        return toResponse(configRepository.save(config));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Status> getForwardReturnAllowedStatuses() {
        return parseStatuses(getOrCreateEntity().getForwardReturnAllowedStatuses());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isForwardReturnAllowed(Status status) {
        if (status == null) return false;
        return getForwardReturnAllowedStatuses().contains(status);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isApproveRejectButtonsEnabled() {
        return getOrCreateEntity().isApproveRejectButtonsEnabled();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MovementActionType> getUndoSendAllowedActions() {
        return parseUndoActions(getOrCreateEntity().getUndoSendAllowedActions());
    }

    @Override
    @Transactional
    public DcAutoForwardConfig getOrCreateEntity() {
        return configRepository.findById(SINGLETON_ID)
                .orElseGet(() -> {
                    // Defaults keep existing workflow behavior available even before an admin opens settings.
                    DcAutoForwardConfig config = new DcAutoForwardConfig();
                    config.setId(SINGLETON_ID);
                    config.setEnabled(false);
                    config.setTimeoutMinutes(60);
                    config.setForwardReturnAllowedStatuses(toCsv(DEFAULT_FORWARD_RETURN_ALLOWED_STATUSES));
                    config.setApproveRejectButtonsEnabled(true);
                    config.setUndoSendEnabled(true);
                    config.setUndoSendWindowHours(24);
                    config.setUndoSendRequiresUnopened(true);
                    config.setUndoSendAllowedActions(toActionCsv(DEFAULT_UNDO_SEND_ALLOWED_ACTIONS));
                    config.setUndoSendRequiresReason(true);
                    config.setUndoSendNotifyReceiver(true);
                    config.setUndoSendShowExpiredInfo(true);
                    config.setUpdatedAt(LocalDateTime.now());
                    return configRepository.save(config);
                });
    }

    private User requireReceiver(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("Receiver user not found: " + userId));

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new BadRequestException("Receiver user is not active.");
        }

        String role = user.getRole() == null ? "" : user.getRole().getRoleName();
        if (!"DDC".equalsIgnoreCase(role) && !"SDDC".equalsIgnoreCase(role)) {
            throw new BadRequestException("Receiver must have DDC or SDDC role.");
        }

        return user;
    }

    private User requireDcUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("DC user not found: " + userId));

        String role = user.getRole() == null ? "" : user.getRole().getRoleName();
        if (!"DC".equalsIgnoreCase(role)) {
            throw new BadRequestException("dcUserId must reference a user with the DC role: " + userId);
        }

        return user;
    }

    /** Full replace: every mapping in the request is upserted; any existing mapping not present is removed. */
    private void saveDcReceiverMappings(List<UpdateDcAutoForwardConfigRequest.Entry> entries) {
        Set<Long> keepDcUserIds = new LinkedHashSet<>();
        for (UpdateDcAutoForwardConfigRequest.Entry entry : entries) {
            requireDcUser(entry.getDcUserId());
            requireReceiver(entry.getReceiverUserId());

            DcAutoForwardReceiver mapping = dcReceiverRepository.findByDcUserId(entry.getDcUserId())
                    .orElseGet(DcAutoForwardReceiver::new);
            mapping.setDcUserId(entry.getDcUserId());
            mapping.setReceiverUserId(entry.getReceiverUserId());
            dcReceiverRepository.save(mapping);
            keepDcUserIds.add(entry.getDcUserId());
        }

        for (DcAutoForwardReceiver existing : dcReceiverRepository.findAllByOrderByDcUserIdAsc()) {
            if (!keepDcUserIds.contains(existing.getDcUserId())) {
                dcReceiverRepository.delete(existing);
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Long> getDcReceiverMapping() {
        return dcReceiverRepository.receiverIdByDcUserId();
    }

    private DcAutoForwardConfigResponse toResponse(DcAutoForwardConfig config) {
        User receiver = null;
        if (config.getReceiverUserId() != null) {
            receiver = userRepository.findById(config.getReceiverUserId()).orElse(null);
        }

        List<DcAutoForwardReceiver> mappings = dcReceiverRepository.findAllByOrderByDcUserIdAsc();
        Set<Long> lookupIds = new LinkedHashSet<>();
        for (DcAutoForwardReceiver m : mappings) {
            lookupIds.add(m.getDcUserId());
            lookupIds.add(m.getReceiverUserId());
        }
        Map<Long, User> usersById = userRepository.findAllById(lookupIds).stream()
                .collect(java.util.stream.Collectors.toMap(User::getId, u -> u));

        List<DcAutoForwardReceiverEntry> dcReceivers = mappings.stream()
                .map(m -> {
                    User dc = usersById.get(m.getDcUserId());
                    User r = usersById.get(m.getReceiverUserId());
                    return DcAutoForwardReceiverEntry.builder()
                            .dcUserId(m.getDcUserId())
                            .dcName(dc == null ? null : dc.getFullName())
                            .receiverUserId(m.getReceiverUserId())
                            .receiverName(r == null ? null : r.getFullName())
                            .receiverRole(r == null || r.getRole() == null ? null : r.getRole().getRoleName())
                            .build();
                })
                .toList();

        return DcAutoForwardConfigResponse.builder()
                .enabled(config.isEnabled())
                .timeoutMinutes(config.getTimeoutMinutes())
                .receiverUserId(config.getReceiverUserId())
                .receiverName(receiver == null ? null : receiver.getFullName())
                .receiverRole(receiver == null || receiver.getRole() == null ? null : receiver.getRole().getRoleName())
                .dcReceivers(dcReceivers)
                .forwardReturnAllowedStatuses(parseStatuses(config.getForwardReturnAllowedStatuses())
                        .stream()
                        .map(Status::name)
                        .toList())
                .approveRejectButtonsEnabled(config.isApproveRejectButtonsEnabled())
                .undoSendEnabled(config.isUndoSendEnabled())
                .undoSendWindowHours(config.getUndoSendWindowHours())
                .undoSendRequiresUnopened(config.isUndoSendRequiresUnopened())
                .undoSendAllowedActions(parseUndoActions(config.getUndoSendAllowedActions()).stream().map(MovementActionType::name).toList())
                .undoSendRequiresReason(config.isUndoSendRequiresReason())
                .undoSendNotifyReceiver(config.isUndoSendNotifyReceiver())
                .undoSendShowExpiredInfo(config.isUndoSendShowExpiredInfo())
                .build();
    }

    private List<Status> normalizeStatuses(List<String> values) {
        Set<Status> statuses = new LinkedHashSet<>();
        for (String raw : values) {
            if (raw == null || raw.isBlank()) continue;
            try {
                statuses.add(Status.valueOf(raw.trim().toUpperCase()));
            } catch (IllegalArgumentException ex) {
                throw new BadRequestException("Invalid workflow status: " + raw);
            }
        }

        if (statuses.isEmpty()) {
            throw new BadRequestException("Select at least one status where Forward/Return is allowed.");
        }

        return new ArrayList<>(statuses);
    }

    private List<Status> parseStatuses(String csv) {
        if (csv == null || csv.isBlank()) {
            return DEFAULT_FORWARD_RETURN_ALLOWED_STATUSES;
        }

        List<String> parts = List.of(csv.split(","));
        try {
            return normalizeStatuses(parts);
        } catch (BadRequestException ex) {
            return DEFAULT_FORWARD_RETURN_ALLOWED_STATUSES;
        }
    }

    private String toCsv(List<Status> statuses) {
        return String.join(",", statuses.stream().map(Status::name).toList());
    }

    private List<MovementActionType> normalizeUndoActions(List<String> values) {
        Set<MovementActionType> actions = new LinkedHashSet<>();
        for (String raw : values) {
            if (raw == null || raw.isBlank()) continue;
            try {
                MovementActionType action = MovementActionType.valueOf(raw.trim().toUpperCase());
                if (action != MovementActionType.FORWARD && action != MovementActionType.RETURN) {
                    throw new BadRequestException("Undo Send can apply only to FORWARD and RETURN.");
                }
                actions.add(action);
            } catch (IllegalArgumentException ex) {
                throw new BadRequestException("Invalid undo send action: " + raw);
            }
        }
        if (actions.isEmpty()) {
            throw new BadRequestException("Select at least one action where Undo Send is allowed.");
        }
        return new ArrayList<>(actions);
    }

    private List<MovementActionType> parseUndoActions(String csv) {
        if (csv == null || csv.isBlank()) {
            return DEFAULT_UNDO_SEND_ALLOWED_ACTIONS;
        }
        try {
            return normalizeUndoActions(List.of(csv.split(",")));
        } catch (BadRequestException ex) {
            return DEFAULT_UNDO_SEND_ALLOWED_ACTIONS;
        }
    }

    private String toActionCsv(List<MovementActionType> actions) {
        return String.join(",", actions.stream().map(MovementActionType::name).toList());
    }
}
