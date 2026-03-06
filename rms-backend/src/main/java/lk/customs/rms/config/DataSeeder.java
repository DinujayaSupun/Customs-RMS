package lk.customs.rms.config;

import lk.customs.rms.entity.Role;
import lk.customs.rms.entity.User;
import lk.customs.rms.repository.RoleRepository;
import lk.customs.rms.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class DataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

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
        // Migration is best-effort only. Seeder must never fail app startup.
        if (!tableExists(jdbcTemplate, "documents")) {
            log.info("Skipping date-column migration: 'documents' table does not exist yet.");
            return;
        }

        migrateColumnToDateTime(jdbcTemplate, "documents", "completed_at");
        migrateColumnToDateTime(jdbcTemplate, "documents", "issued_at");
    }

    private void migrateColumnToDateTime(JdbcTemplate jdbcTemplate, String tableName, String columnName) {
        if (!columnExists(jdbcTemplate, tableName, columnName)) {
            log.info("Skipping migration for {}.{}: column not found.", tableName, columnName);
            return;
        }

        try {
            jdbcTemplate.execute("ALTER TABLE " + tableName + " MODIFY COLUMN " + columnName + " DATETIME(6) NULL");
        } catch (Exception ex) {
            log.warn("Skipping migration for {}.{} due to: {}", tableName, columnName, ex.getMessage());
        }
    }

    private boolean tableExists(JdbcTemplate jdbcTemplate, String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
                Integer.class,
                tableName
        );
        return count != null && count > 0;
    }

    private boolean columnExists(JdbcTemplate jdbcTemplate, String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                Integer.class,
                tableName,
                columnName
        );
        return count != null && count > 0;
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

        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException ex) {
            // Another run may have inserted the same username in parallel; do not fail startup.
            log.warn("Skipping seed user '{}': {}", username, ex.getMostSpecificCause().getMessage());
        }
    }
}
