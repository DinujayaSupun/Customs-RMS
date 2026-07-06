-- V4: per-DC auto-forward receiver mapping.
-- Replaces the single global auto-forward receiver: each DC (dc_user_id) maps to its own
-- DDC/SDDC receiver (receiver_user_id). A DC with no row here is skipped by the scheduler.
CREATE TABLE dc_auto_forward_receiver (
    dc_auto_forward_receiver_id BIGINT NOT NULL AUTO_INCREMENT,
    dc_user_id                  BIGINT NOT NULL,
    receiver_user_id            BIGINT NOT NULL,
    PRIMARY KEY (dc_auto_forward_receiver_id),
    UNIQUE KEY uk_dc_auto_forward_receiver_dc_user (dc_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
