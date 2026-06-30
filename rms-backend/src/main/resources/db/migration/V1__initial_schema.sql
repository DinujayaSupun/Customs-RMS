-- V1: initial schema baseline.
-- Generated from the current Hibernate-managed MySQL schema (12 tables).
-- Foreign-key checks are disabled around the CREATE statements because tables are
-- emitted alphabetically and some reference tables defined later in the file.

SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE `audit_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `action_type` varchar(255) NOT NULL,
  `details_json` text,
  `entity_id` bigint NOT NULL,
  `entity_type` varchar(255) NOT NULL,
  `message` text,
  `performed_at` datetime(6) NOT NULL,
  `performed_by_user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_audit_performed_at_id` (`performed_at`,`id`),
  KEY `idx_audit_action_performed_at` (`action_type`,`performed_at`),
  KEY `idx_audit_user_performed_at` (`performed_by_user_id`,`performed_at`),
  KEY `idx_audit_entity` (`entity_type`,`entity_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `dc_auto_forward_config` (
  `config_id` bigint NOT NULL,
  `approve_reject_buttons_enabled` bit(1) NOT NULL,
  `enabled` bit(1) NOT NULL,
  `forward_return_allowed_statuses` varchar(200) NOT NULL,
  `receiver_user_id` bigint DEFAULT NULL,
  `timeout_minutes` int NOT NULL,
  `undo_send_allowed_actions` varchar(80) NOT NULL,
  `undo_send_enabled` bit(1) NOT NULL,
  `undo_send_notify_receiver` bit(1) NOT NULL,
  `undo_send_requires_reason` bit(1) NOT NULL,
  `undo_send_requires_unopened` bit(1) NOT NULL,
  `undo_send_show_expired_info` bit(1) NOT NULL,
  `undo_send_window_hours` int NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`config_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `document_attachments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `is_deleted` bit(1) NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `deleted_by` bigint DEFAULT NULL,
  `document_id` bigint NOT NULL,
  `file_name` varchar(255) NOT NULL,
  `file_path` varchar(1000) NOT NULL,
  `is_latest` bit(1) NOT NULL,
  `uploaded_at` datetime(6) NOT NULL,
  `uploaded_by` bigint NOT NULL,
  `version_no` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_attachments_document_latest_deleted` (`document_id`,`is_latest`,`is_deleted`),
  KEY `idx_attachments_document_version_deleted` (`document_id`,`version_no`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `document_movements` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `action_at` datetime(6) NOT NULL,
  `action_by_user_id` bigint NOT NULL,
  `action_type` enum('APPROVE','CREATE','FORWARD','ISSUE','REJECT','REOPEN','RETURN','UNDO_SEND','UPDATE') NOT NULL,
  `document_id` bigint NOT NULL,
  `forward_visibility` varchar(10) DEFAULT NULL,
  `from_user_id` bigint DEFAULT NULL,
  `to_user_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_movements_document_action_at` (`document_id`,`action_at`,`id`),
  KEY `idx_movements_action_by_type_at` (`action_by_user_id`,`action_type`,`action_at`,`id`),
  KEY `idx_movements_from_type_at` (`from_user_id`,`action_type`,`action_at`,`id`),
  KEY `idx_movements_to_type_at` (`to_user_id`,`action_type`,`action_at`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `document_recipient_sets` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `is_active` bit(1) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `created_by_user_id` bigint NOT NULL,
  `document_id` bigint NOT NULL,
  `movement_id` bigint DEFAULT NULL,
  `reason` enum('AUTO_FORWARD','CREATE','FORWARD','MANUAL_UPDATE','REOPEN','RETURN','UNDO_SEND') NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_recipient_sets_document_active` (`document_id`,`is_active`),
  KEY `idx_recipient_sets_document_created` (`document_id`,`created_at`,`id`),
  KEY `idx_recipient_sets_movement` (`movement_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `document_recipients` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `added_at` datetime(6) NOT NULL,
  `added_by_user_id` bigint NOT NULL,
  `document_id` bigint NOT NULL,
  `recipient_set_id` bigint NOT NULL,
  `recipient_type` enum('BCC','CC','TO') NOT NULL,
  `removed_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_recipients_set` (`recipient_set_id`),
  KEY `idx_recipients_document_user` (`document_id`,`user_id`),
  KEY `idx_recipients_user_type` (`user_id`,`recipient_type`),
  KEY `idx_recipients_document_type` (`document_id`,`recipient_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `document_remarks` (
  `remark_id` bigint NOT NULL AUTO_INCREMENT,
  `document_id` bigint NOT NULL,
  `remark_text` text NOT NULL,
  `remarked_at` datetime(6) NOT NULL,
  `remarked_by` bigint NOT NULL,
  PRIMARY KEY (`remark_id`),
  KEY `idx_remarks_document_remarked_at` (`document_id`,`remarked_at`),
  KEY `idx_remarks_document_user_remarked_at` (`document_id`,`remarked_by`,`remarked_at`),
  KEY `FKng4iltn2faull1ipy6bmiwrkj` (`remarked_by`),
  CONSTRAINT `FKcd2xl4st487jaibadxtw7i4rm` FOREIGN KEY (`document_id`) REFERENCES `documents` (`id`),
  CONSTRAINT `FKng4iltn2faull1ipy6bmiwrkj` FOREIGN KEY (`remarked_by`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `document_user_views` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `document_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `viewed_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_document_user_view` (`document_id`,`user_id`),
  KEY `idx_document_user_view_document` (`document_id`),
  KEY `idx_document_user_view_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `documents` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `company_name` varchar(255) DEFAULT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `created_by_user_id` bigint NOT NULL,
  `current_owner_user_id` bigint NOT NULL,
  `dc_assigned_at` datetime(6) DEFAULT NULL,
  `dc_viewed_at` datetime(6) DEFAULT NULL,
  `is_deleted` bit(1) NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `deleted_by_user_id` bigint DEFAULT NULL,
  `issued_at` datetime(6) DEFAULT NULL,
  `priority` enum('HIGH','LOW','MEDIUM','URGENT') NOT NULL,
  `received_date` date NOT NULL,
  `ref_no` varchar(255) NOT NULL,
  `status` enum('APPROVED','IN_PROGRESS','ISSUED','PENDING','REJECTED','RETURNED') NOT NULL,
  `title` varchar(255) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `version` bigint DEFAULT NULL,
  `visibility` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKeh5v93eo7sewvs2etj0q08d7y` (`ref_no`),
  KEY `idx_documents_owner_status_deleted_updated` (`current_owner_user_id`,`status`,`is_deleted`,`updated_at`),
  KEY `idx_documents_deleted_updated_id` (`is_deleted`,`updated_at`,`id`),
  KEY `idx_documents_created_by_deleted` (`created_by_user_id`,`is_deleted`),
  KEY `idx_documents_received_date` (`received_date`),
  KEY `idx_documents_visibility_deleted` (`visibility`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `role_permissions` (
  `role_permission_id` bigint NOT NULL AUTO_INCREMENT,
  `enabled` bit(1) NOT NULL,
  `permission_name` varchar(80) NOT NULL,
  `role_id` bigint NOT NULL,
  PRIMARY KEY (`role_permission_id`),
  UNIQUE KEY `uk_role_permissions_role_permission` (`role_id`,`permission_name`),
  CONSTRAINT `FKn5fotdgk8d1xvo8nav9uv3muc` FOREIGN KEY (`role_id`) REFERENCES `roles` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `roles` (
  `role_id` bigint NOT NULL AUTO_INCREMENT,
  `role_name` varchar(50) NOT NULL,
  PRIMARY KEY (`role_id`),
  UNIQUE KEY `UK716hgxp60ym1lifrdgp67xt5k` (`role_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `users` (
  `user_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `deleted_by_user_id` bigint DEFAULT NULL,
  `department` varchar(120) DEFAULT NULL,
  `email` varchar(150) DEFAULT NULL,
  `full_name` varchar(150) NOT NULL,
  `is_active` bit(1) NOT NULL,
  `is_deleted` bit(1) NOT NULL,
  `password_hash` varchar(255) NOT NULL,
  `phone` varchar(30) DEFAULT NULL,
  `profile_picture_content_type` varchar(80) DEFAULT NULL,
  `profile_picture_path` varchar(255) DEFAULT NULL,
  `profile_picture_updated_at` datetime(6) DEFAULT NULL,
  `username` varchar(80) NOT NULL,
  `role_id` bigint NOT NULL,
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `UKr43af9ap4edm43mmtq01oddj6` (`username`),
  KEY `idx_users_username` (`username`),
  KEY `idx_users_active_deleted_name` (`is_active`,`is_deleted`,`full_name`),
  KEY `FKp56c1712k691lhsyewcssf40f` (`role_id`),
  CONSTRAINT `FKp56c1712k691lhsyewcssf40f` FOREIGN KEY (`role_id`) REFERENCES `roles` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET FOREIGN_KEY_CHECKS = 1;
