package lk.customs.rms.service.impl;

import lk.customs.rms.dto.DcAutoForwardConfigResponse;
import lk.customs.rms.dto.UpdateDcAutoForwardConfigRequest;
import lk.customs.rms.entity.DcAutoForwardConfig;
import lk.customs.rms.entity.User;
import lk.customs.rms.exception.BadRequestException;
import lk.customs.rms.repository.DcAutoForwardConfigRepository;
import lk.customs.rms.repository.UserRepository;
import lk.customs.rms.service.DcAutoForwardConfigService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class DcAutoForwardConfigServiceImpl implements DcAutoForwardConfigService {

    private static final Long SINGLETON_ID = 1L;

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

        config.setUpdatedAt(LocalDateTime.now());
        return toResponse(configRepository.save(config));
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
                .build();
    }
}
