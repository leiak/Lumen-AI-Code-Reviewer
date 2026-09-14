CREATE TABLE IF NOT EXISTS review_sessions (
    id TEXT PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    config_hash TEXT NOT NULL,
    config_snapshot TEXT NOT NULL,
    status TEXT NOT NULL,
    current_node TEXT,
    diff_hash TEXT NOT NULL,
    diff_content TEXT NOT NULL,
    metadata TEXT
);

CREATE TABLE IF NOT EXISTS state_snapshots (
    session_id TEXT NOT NULL REFERENCES review_sessions(id),
    node_name TEXT NOT NULL,
    round INTEGER NOT NULL,
    state_json TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (session_id, node_name, round)
);

CREATE TABLE IF NOT EXISTS audit_events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id TEXT NOT NULL,
    node_name TEXT,
    event_type TEXT NOT NULL,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    payload TEXT NOT NULL,
    parent_event_id INTEGER
);

CREATE TABLE IF NOT EXISTS llm_calls (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id TEXT NOT NULL,
    node_name TEXT NOT NULL,
    model TEXT NOT NULL,
    prompt_tokens INTEGER,
    completion_tokens INTEGER,
    cost_usd REAL,
    latency_ms INTEGER,
    success INTEGER NOT NULL,
    error TEXT,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tool_calls (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id TEXT NOT NULL,
    tool_name TEXT NOT NULL,
    arguments TEXT,
    result TEXT,
    success INTEGER NOT NULL,
    error TEXT,
    latency_ms INTEGER,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_sessions_status ON review_sessions(status);
CREATE INDEX idx_snapshots_session ON state_snapshots(session_id);
CREATE INDEX idx_audit_session ON audit_events(session_id, timestamp);
