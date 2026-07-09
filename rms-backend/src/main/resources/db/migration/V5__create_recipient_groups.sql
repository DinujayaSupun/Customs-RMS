-- V5: recipient groups (WhatsApp-style group routing). A group has admins + members; a document
-- forwarded to a group is held by the group (any admin can act on it, members are CC).
CREATE TABLE recipient_group (
    recipient_group_id  BIGINT NOT NULL AUTO_INCREMENT,
    name                 VARCHAR(120) NOT NULL,
    color                VARCHAR(20),
    image_path           VARCHAR(255),
    created_by_user_id   BIGINT NOT NULL,
    created_at           DATETIME(6) NOT NULL,
    updated_at           DATETIME(6),
    PRIMARY KEY (recipient_group_id),
    UNIQUE KEY uk_recipient_group_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE recipient_group_member (
    recipient_group_member_id BIGINT NOT NULL AUTO_INCREMENT,
    group_id                   BIGINT NOT NULL,
    user_id                    BIGINT NOT NULL,
    is_admin                   BIT(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (recipient_group_member_id),
    UNIQUE KEY uk_recipient_group_member (group_id, user_id),
    KEY idx_recipient_group_member_group (group_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Group-held documents: non-null means the document is held by this group rather than a single
-- person (current_owner_user_id remains a compatibility anchor pointing at one of the admins).
ALTER TABLE documents ADD COLUMN current_owner_group_id BIGINT NULL;
