-- V3: per-user permission overrides.
-- A row overrides the user's role for one permission: enabled=1 grants, enabled=0 revokes.
-- No row for a (user, permission) pair means the role's value is inherited.
CREATE TABLE user_permissions (
    user_permission_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id            BIGINT NOT NULL,
    permission_name    VARCHAR(80) NOT NULL,
    enabled            BIT(1) NOT NULL,
    PRIMARY KEY (user_permission_id),
    UNIQUE KEY uk_user_permissions_user_permission (user_id, permission_name),
    KEY idx_user_permissions_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
