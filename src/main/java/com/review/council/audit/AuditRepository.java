package com.review.council.audit;

import javax.sql.DataSource;
import java.sql.*;

public class AuditRepository {
    private final DataSource ds;
    public AuditRepository(DataSource ds) { this.ds = ds; }

    public void record(String sessionId, String node, String type, String payloadJson) {
        var sql = "INSERT INTO audit_events (session_id, node_name, event_type, payload) VALUES (?,?,?,?)";
        try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            ps.setString(2, node);
            ps.setString(3, type);
            ps.setString(4, payloadJson);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public int countFor(String sessionId) {
        try (var c = ds.getConnection(); var ps = c.prepareStatement("SELECT COUNT(*) FROM audit_events WHERE session_id = ?")) {
            ps.setString(1, sessionId);
            try (var rs = ps.executeQuery()) { return rs.next() ? rs.getInt(1) : 0; }
        } catch (SQLException e) { throw new RuntimeException(e); }
    }
}
