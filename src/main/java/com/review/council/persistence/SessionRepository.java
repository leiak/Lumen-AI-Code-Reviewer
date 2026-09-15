package com.review.council.persistence;

import javax.sql.DataSource;
import java.sql.*;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class SessionRepository {
    private final DataSource ds;
    public SessionRepository(DataSource ds) { this.ds = ds; }

    public void insert(String id, String configHash, String configSnapshot,
                       String status, String diffHash, String diffContent) {
        var sql = "INSERT INTO review_sessions (id, config_hash, config_snapshot, status, diff_hash, diff_content) VALUES (?,?,?,?,?,?)";
        try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, configHash);
            ps.setString(3, configSnapshot);
            ps.setString(4, status);
            ps.setString(5, diffHash);
            ps.setString(6, diffContent);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public Optional<Session> load(String id) {
        var sql = "SELECT id, config_hash, config_snapshot, status, current_node, diff_hash, diff_content, scan_root, total_files, total_chunks FROM review_sessions WHERE id = ?";
        try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new Session(
                    rs.getString("id"),
                    rs.getString("config_hash"),
                    rs.getString("config_snapshot"),
                    rs.getString("status"),
                    rs.getString("current_node"),
                    rs.getString("diff_hash"),
                    rs.getString("diff_content"),
                    rs.getString("scan_root"),
                    rs.getObject("total_files") != null ? rs.getInt("total_files") : null,
                    rs.getObject("total_chunks") != null ? rs.getInt("total_chunks") : null
                ));
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public void updateStatus(String id, String status, String currentNode) {
        var sql = "UPDATE review_sessions SET status = ?, current_node = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, currentNode);
            ps.setString(3, id);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    /** Persist scan metadata after scan() completes so SessionDetail can render it. */
    public void recordScanMetadata(String id, String scanRoot, int totalFiles, int totalChunks) {
        var sql = "UPDATE review_sessions SET scan_root = ?, total_files = ?, total_chunks = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
            ps.setString(1, scanRoot);
            ps.setInt(2, totalFiles);
            ps.setInt(3, totalChunks);
            ps.setString(4, id);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public record Session(String id, String configHash, String configSnapshot,
                          String status, String currentNode, String diffHash, String diffContent,
                          String scanRoot, Integer totalFiles, Integer totalChunks) {}
}
