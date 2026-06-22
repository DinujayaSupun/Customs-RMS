package lk.customs.rms;

import lk.customs.rms.security.LoginAttemptService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptServiceTests {

    @Test
    void blocksAKeyOnlyAfterReachingTheFailureThreshold() {
        LoginAttemptService service = new LoginAttemptService(3, 15);
        String key = "ip:198.51.100.10";

        assertThat(service.isBlocked(key)).isFalse();
        service.recordFailure(key);
        service.recordFailure(key);
        assertThat(service.isBlocked(key)).isFalse(); // 2 failures, still under the limit of 3
        service.recordFailure(key);
        assertThat(service.isBlocked(key)).isTrue();   // 3rd failure trips the block
    }

    @Test
    void successResetClearsTheFailureCounter() {
        LoginAttemptService service = new LoginAttemptService(3, 15);
        String key = "ip:198.51.100.11";

        service.recordFailure(key);
        service.recordFailure(key);
        service.recordFailure(key);
        assertThat(service.isBlocked(key)).isTrue();

        service.reset(key);
        assertThat(service.isBlocked(key)).isFalse();
    }

    @Test
    void blockingOneKeyDoesNotAffectAnother() {
        LoginAttemptService service = new LoginAttemptService(2, 15);
        String blocked = "ip:198.51.100.20";
        String other = "ip:198.51.100.21";

        service.recordFailure(blocked);
        service.recordFailure(blocked);

        assertThat(service.isBlocked(blocked)).isTrue();
        assertThat(service.isBlocked(other)).isFalse();
    }
}
