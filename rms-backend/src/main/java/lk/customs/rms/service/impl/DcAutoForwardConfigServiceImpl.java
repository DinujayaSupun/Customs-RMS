package lk.customs.rms.service.impl;

import lk.customs.rms.dto.DcAutoForwardConfigResponse;
import lk.customs.rms.dto.UpdateDcAutoForwardConfigRequest;
import lk.customs.rms.entity.DcAutoForwardConfig;
import lk.customs.rms.entity.User;
import lk.customs.rms.enums.Status;
import lk.customs.rms.exception.BadRequestException;
import lk.customs.rms.repository.DcAutoForwardConfigRepository;
import lk.customs.rms.repository.UserRepository;
import lk.customs.rms.service.DcAutoForwardConfigService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class DcAutoForwardConfigServiceImpl implements DcAutoForwardConfigService {

    private static final Long SINGLETON_ID = 1L;
    private static final List<Status> DEFAULT_FORWARD_RETURN_ALLOWED_STATUSES =
            List.of(Status.PENDING, Status.IN_PROGRESS, Status.RETURNED);

    private final DcAutoForwardConfigRepository configRepository;
    private final UserRepository userRepository;

    public DcAutoForwardConfigServiceImpl(DcAutoForwardConfigRepository configRepository,
                                          UserRepository userRepository) {
        this.configRepository = configRepository;
        this.userRepository = userRepository;
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

        Long receiverUserId = request.getReceiverUserId();
        if (config.isEnabled()) {
            if (receiverUserId == null) {
                throw new BadRequestException("Receiver user is required when DC auto-forward is enabled.");
            }
            User receiver = requireReceiver(receiverUserId);
            config.setReceiverUserId(receiver.getId());
        } else {
            if (receiverUserId == null) {
                config.setReceiverUserId(null);
            } else {
                User receiver = requireReceiver(receiverUserId);
                config.setReceiverUserId(receiver.getId());
            }
        }

        if (request.getForwardReturnAllowedStatuses() != null) {
            List<Status> allowedStatuses = normalizeStatuses(request.getForwardReturnAllowedStatuses());
            config.setForwardReturnAllowedStatuses(toCsv(allowedStatuses));
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
    @Transactional
    public DcAutoForwardConfig getOrCreateEntity() {
        return configRepository.findById(SINGLETON_ID)
                .orElseGet(() -> {
                    DcAutoForwardConfig config = new DcAutoForwardConfig();
                    config.setId(SINGLETON_ID);
                    config.setEnabled(false);
                    config.setTimeoutMinutes(60);
                    config.setForwardReturnAllowedStatuses(toCsv(DEFAULT_FORWARD_RETURN_ALLOWED_STATUSES));
                    config.setApproveRejectButtonsEnabled(true);
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

    private DcAutoForwardConfigResponse toResponse(DcAutoForwardConfig config) {
        User receiver = null;
        if (config.getReceiverUserId() != null) {
            receiver = userRepository.findById(config.getReceiverUserId()).orElse(null);
        }

        return DcAutoForwardConfigResponse.builder()
                .enabled(config.isEnabled())
                .timeoutMinutes(config.getTimeoutMinutes())
                .receiverUserId(config.getReceiverUserId())
                .receiverName(receiver == null ? null : receiver.getFullName())
                .receiverRole(receiver == null || receiver.getRole() == null ? null : receiver.getRole().getRoleName())
                .forwardReturnAllowedStatuses(parseStatuses(config.getForwardReturnAllowedStatuses())
                        .stream()
                        .map(Status::name)
                        .toList())
                .approveRejectButtonsEnabled(config.isApproveRejectButtonsEnabled())
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
}
