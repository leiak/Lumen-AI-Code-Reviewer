package com.review.council.persistence;

import javax.sql.DataSource;
import java.sql.*;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class StateSnapshotRepository {
    private final DataSource ds;
    public StateSnapshotRepository(DataSource ds) { this.ds = ds; }

    public void save(String sessionId, String nodeName, int round, String stateJson) {
        var sql = "INSERT INTO state_snapshots (session_id, node_name, round, state_json) VALUES (?,?,?,?)";
        try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            ps.setString(2, nodeName);
            ps.setInt(3, round);
            ps.setString(4, stateJson);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public Optional<Snapshot> loadLatest(String sessionId) {
        var sql = "SELECT node_name, round, state_json, created_at FROM state_snapshots WHERE session_id = ? ORDER BY rowid DESC LIMIT 1";
        try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new Snapshot(
                    rs.getString("node_name"), rs.getInt("round"),
                    rs.getString("state_json"), rs.getTimestamp("created_at").toString()));
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public record Snapshot(String nodeName, int round, String stateJson, String createdAt) {}
}
