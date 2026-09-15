-- V2: scan-whole-project metadata.
-- Note: SQLite ALTER TABLE ADD COLUMN has no IF NOT EXISTS clause.
-- DataSourceConfig.applyMigration handles idempotency by checking
-- PRAGMA table_info before issuing ALTER for each column.

CREATE INDEX IF NOT EXISTS idx_audit_chunk ON audit_events(session_id, chunk_id);