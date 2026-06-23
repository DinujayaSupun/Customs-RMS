package lk.customs.rms.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class DocumentDateColumnMigration {

    private static final Logger log = LoggerFactory.getLogger(DocumentDateColumnMigration.class);

    @Bean
    CommandLineRunner migrateDocumentDateColumnsRunner(JdbcTemplate jdbcTemplate) {
        return args -> migrateDocumentDateColumns(jdbcTemplate);
    }

    // Keep legacy/local databases compatible without requiring a full migration tool for this small schema change.
    private void migrateDocumentDateColumns(JdbcTemplate jdbcTemplate) {
        // Migration is best-effort only and must not fail app startup.
        if (!tableExists(jdbcTemplate, "documents")) {
            log.info("Skipping date-column migration: 'documents' table does not exist yet.");
        } else {
            addColumnIfMissing(jdbcTemplate, "documents", "updated_at", "DATETIME(6) NULL");
            migrateColumnToDateTime(jdbcTemplate, "documents", "completed_at");
            migrateColumnToDateTime(jdbcTemplate, "documents", "issued_at");
        }

        if (!tableExists(jdbcTemplate, "users")) {
            log.info("Skipping date-column migration: 'users' table does not exist yet.");
            return;
        }

        addColumnIfMissing(jdbcTemplate, "users", "profile_picture_path", "VARCHAR(255) NULL");
        addColumnIfMissing(jdbcTemplate, "users", "profile_picture_content_type", "VARCHAR(80) NULL");
        addColumnIfMissing(jdbcTemplate, "users", "profile_picture_updated_at", "DATETIME(6) NULL");
    }

    private void addColumnIfMissing(JdbcTemplate jdbcTemplate, String tableName, String columnName, String columnDefinition) {
        if (columnExists(jdbcTemplate, tableName, columnName)) {
            return;
        }

        try {
            jdbcTemplate.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnDefinition);
        } catch (Exception ex) {
            log.warn("Skipping add-column migration for {}.{} due to: {}", tableName, columnName, ex.getMessage());
        }
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
}
