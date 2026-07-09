-- V6: record which group (if any) a FORWARD movement targeted, so the movement timeline and
-- sent-messages list can show "Forwarded to Group: X" instead of only the anchor admin's name.
ALTER TABLE document_movements ADD COLUMN to_group_id BIGINT NULL;
