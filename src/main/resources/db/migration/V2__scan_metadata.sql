ALTER TABLE review_sessions ADD COLUMN scan_root TEXT;
ALTER TABLE review_sessions ADD COLUMN total_files INTEGER;
ALTER TABLE review_sessions ADD COLUMN total_chunks INTEGER;

-- Per-call progress: each (chunkId, reviewer) pair logs one row
ALTER TABLE audit_events ADD COLUMN chunk_id TEXT;
CREATE INDEX IF NOT EXISTS idx_audit_chunk ON audit_events(session_id, chunk_id);