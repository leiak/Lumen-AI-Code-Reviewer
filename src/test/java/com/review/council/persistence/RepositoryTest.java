package com.review.council.persistence;

import com.review.council.audit.AuditRepository;
import com.review.council.audit.LlmCallRepository;
import org.junit.jupiter.api.*;
import org.sqlite.SQLiteDataSource;
import javax.sql.DataSource;
import java.nio.file.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class RepositoryTest {
    private static DataSource ds;
    private SessionRepository sessions;
    private StateSnapshotRepository snapshots;
    private AuditRepository audit;
    private LlmCallRepository llm;
    private String sessionId;

    @BeforeAll
    static void setup() throws Exception {
        var tmp = Files.createTempFile("test-", ".db");
        var src = new SQLiteDataSource();
        src.setUrl("jdbc:sqlite:" + tmp);
        try (var c = src.getConnection(); var st = c.createStatement()) {
            var sql = Files.readString(Paths.get("src/main/resources/db/migration/V1__init.sql"));
            for (var stmt : sql.split(";")) {
                var t = stmt.trim();
                if (!t.isEmpty()) st.execute(t);
            }
        }
        ds = src;
    }

    @BeforeEach
    void init() {
        sessions = new SessionRepository(ds);
        snapshots = new StateSnapshotRepository(ds);
        audit = new AuditRepository(ds);
        llm = new LlmCallRepository(ds);
        sessionId = "s-" + UUID.randomUUID().toString().substring(0, 8);
        sessions.insert(sessionId, "hash", "yaml", "running", "d-hash", "d-content");
    }

    @Test
    void session_insert_andLoad() {
        var loaded = sessions.load(sessionId);
        assertThat(loaded).isPresent();
        assertThat(loaded.get().status()).isEqualTo("running");
    }

    @Test
    void session_updateStatus() {
        sessions.updateStatus(sessionId, "paused", "gate_evaluator");
        var s = sessions.load(sessionId).get();
        assertThat(s.status()).isEqualTo("paused");
        assertThat(s.currentNode()).isEqualTo("gate_evaluator");
    }

    @Test
    void snapshot_save_andLoadLatest() {
        snapshots.save(sessionId, "planner", 0, "{\"a\":1}");
        snapshots.save(sessionId, "aggregator", 0, "{\"a\":2}");
        var latest = snapshots.loadLatest(sessionId);
        assertThat(latest).isPresent();
        assertThat(latest.get().nodeName()).isEqualTo("aggregator");
    }

    @Test
    void audit_records_events() {
        audit.record(sessionId, "reviewer_security", "llm_call", "{\"model\":\"gpt-5\"}");
        audit.record(sessionId, "gate_evaluator", "gate_decision", "{\"action\":\"approve\"}");
        assertThat(audit.countFor(sessionId)).isEqualTo(2);
    }

    @Test
    void llmCall_recordsMetrics() {
        llm.record(sessionId, "reviewer_security", "gpt-5", 1000, 200, 0.05, 4500, true, null);
        llm.record(sessionId, "fixer", "claude-opus", 500, 100, 0.02, 3000, true, null);
        assertThat(llm.totalCostFor(sessionId)).isEqualTo(0.07);
        assertThat(llm.totalTokensFor(sessionId)).isEqualTo(1800);
    }
}
