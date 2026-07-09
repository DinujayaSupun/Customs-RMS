-- V2: Internal/External document type.
-- Nullable so pre-existing rows remain valid (they have no type); new documents require it
-- at the application layer (CreateDocumentRequest @NotNull). No backfill: legacy rows stay NULL
-- and display as unspecified.
ALTER TABLE documents ADD COLUMN doc_type VARCHAR(20);
