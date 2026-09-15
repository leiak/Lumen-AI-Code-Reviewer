package com.review.council.audit;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class LlmCallRepository {
    private final DataSource ds;
    public LlmCallRepository(DataSource ds) { this.ds = ds; }

    public void record(String sessionId, String node, String model,
                       int promptTokens, int completionTokens, double costUsd,
                       long latencyMs, boolean success, String error) {
        var sql = "INSERT INTO llm_calls (session_id, node_name, model, prompt_tokens, completion_tokens, cost_usd, latency_ms, success, error) VALUES (?,?,?,?,?,?,?,?,?)";
        try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            ps.setString(2, node);
            ps.setString(3, model);
            ps.setInt(4, promptTokens);
            ps.setInt(5, completionTokens);
            ps.setDouble(6, costUsd);
            ps.setLong(7, latencyMs);
            ps.setInt(8, success ? 1 : 0);
            ps.setString(9, error);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public double totalCostFor(String sessionId) {
        try (var c = ds.getConnection(); var ps = c.prepareStatement("SELECT COALESCE(SUM(cost_usd),0) FROM llm_calls WHERE session_id = ?")) {
            ps.setString(1, sessionId);
            try (var rs = ps.executeQuery()) { return rs.next() ? rs.getDouble(1) : 0; }
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public long totalTokensFor(String sessionId) {
        try (var c = ds.getConnection(); var ps = c.prepareStatement("SELECT COALESCE(SUM(prompt_tokens + completion_tokens),0) FROM llm_calls WHERE session_id = ?")) {
            ps.setString(1, sessionId);
            try (var rs = ps.executeQuery()) { return rs.next() ? rs.getLong(1) : 0; }
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    /** Returns totals grouped by node_name, e.g. {"reviewer_security": NodeTotals(...), ...}. */
    public Map<String, NodeTotals> totalsByNode(String sessionId) {
        var sql = "SELECT node_name, " +
            "COALESCE(SUM(prompt_tokens),0) AS pt, " +
            "COALESCE(SUM(completion_tokens),0) AS ct, " +
            "COALESCE(SUM(cost_usd),0) AS cost, " +
            "COUNT(*) AS calls " +
            "FROM llm_calls WHERE session_id = ? AND success = 1 " +
            "GROUP BY node_name";
        Map<String, NodeTotals> out = new LinkedHashMap<>();
        try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.put(rs.getString("node_name"), new NodeTotals(
                        rs.getLong("pt"), rs.getLong("ct"),
                        rs.getDouble("cost"), rs.getInt("calls")));
                }
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
        return out;
    }

    public record NodeTotals(long promptTokens, long completionTokens, double costUsd, int calls) {}
}
