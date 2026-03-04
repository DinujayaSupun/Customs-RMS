package lk.customs.rms.security;

import lk.customs.rms.entity.User;
import lk.customs.rms.exception.BadRequestException;
import lk.customs.rms.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User requireUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new BadRequestException("Unauthenticated request.");
        }

        String username = authentication.getName();
        return userRepository.findByUsernameAndIsActiveTrue(username)
                .orElseThrow(() -> new BadRequestException("Active user not found for authentication principal."));
    }

    public Long requireUserId(Authentication authentication) {
        return requireUser(authentication).getId();
    }
}
