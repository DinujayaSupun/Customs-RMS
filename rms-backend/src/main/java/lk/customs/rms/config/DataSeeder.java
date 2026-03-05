package lk.customs.rms.config;

import lk.customs.rms.entity.Role;
import lk.customs.rms.entity.User;
import lk.customs.rms.repository.RoleRepository;
import lk.customs.rms.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class DataSeeder {

    @Bean
    CommandLineRunner seedUsers(RoleRepository roleRepository,
                                UserRepository userRepository,
                                PasswordEncoder passwordEncoder,
                                JdbcTemplate jdbcTemplate) {
        return args -> {
            migrateDocumentDateColumns(jdbcTemplate);

            Role dc = ensureRole(roleRepository, "DC");
            Role ddc = ensureRole(roleRepository, "DDC");
            Role sc = ensureRole(roleRepository, "SC");
            Role asc = ensureRole(roleRepository, "ASC");
            Role pma = ensureRole(roleRepository, "PMA");
            Role admin = ensureRole(roleRepository, "ADMIN");

            String defaultPasswordHash = passwordEncoder.encode("Pass@123");

            ensureUser(userRepository, "dc", "Director Customs", defaultPasswordHash, dc);
            ensureUser(userRepository, "ddc", "Deputy Director Customs", defaultPasswordHash, ddc);
            ensureUser(userRepository, "sc", "Senior Superintendent", defaultPasswordHash, sc);
            ensureUser(userRepository, "asc", "Assistant Superintendent", defaultPasswordHash, asc);
            ensureUser(userRepository, "pma", "Personal Management Assistant", defaultPasswordHash, pma);
            ensureUser(userRepository, "admin", "System Administrator", passwordEncoder.encode("Admin@123"), admin);
        };
    }

    private void migrateDocumentDateColumns(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("ALTER TABLE documents MODIFY COLUMN completed_at DATETIME(6) NULL");
        jdbcTemplate.execute("ALTER TABLE documents MODIFY COLUMN issued_at DATETIME(6) NULL");
    }

    private Role ensureRole(RoleRepository roleRepository, String roleName) {
        return roleRepository.findByRoleName(roleName)
                .orElseGet(() -> roleRepository.save(new Role(roleName)));
    }

    private void ensureUser(UserRepository userRepository,
                            String username,
                            String fullName,
                            String passwordHash,
                            Role role) {
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            return;
        }

        User user = new User();
        user.setUsername(username);
        user.setFullName(fullName);
        user.setPasswordHash(passwordHash);
        user.setRole(role);
        user.setIsActive(true);
        userRepository.save(user);
    }
}
