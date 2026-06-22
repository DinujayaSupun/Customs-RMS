package lk.customs.rms;

import lk.customs.rms.entity.User;
import lk.customs.rms.repository.DocumentRepository;
import lk.customs.rms.repository.RoleRepository;
import lk.customs.rms.repository.UserRepository;
import lk.customs.rms.service.FileStorageService;
import lk.customs.rms.service.impl.AdminUserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminUserServiceImplCsvTests {

    private UserRepository userRepository;
    private AdminUserServiceImpl adminUserService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        adminUserService = new AdminUserServiceImpl(
                userRepository,
                mock(RoleRepository.class),
                mock(DocumentRepository.class),
                mock(PasswordEncoder.class),
                mock(FileStorageService.class)
        );
    }

    @Test
    void exportCsvNeutralizesFormulaInjectionInUserFields() {
        User attacker = new User();
        attacker.setFullName("=HYPERLINK(\"http://evil\")");
        attacker.setUsername("attacker");
        attacker.setIsActive(true);

        when(userRepository.searchUsers(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(attacker)));

        String csv = adminUserService.exportCsv(null, null, null);

        // The fullName cell begins with '=' and would execute as a formula in a spreadsheet, so the
        // export must prefix it with an apostrophe. The raw "=HYPERLINK cell must never be emitted.
        assertThat(csv).contains("\"'=HYPERLINK");
        assertThat(csv).doesNotContain("\"=HYPERLINK");
    }
}
