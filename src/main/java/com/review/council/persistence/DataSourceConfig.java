package com.review.council.persistence;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.sqlite.SQLiteDataSource;
import javax.sql.DataSource;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Configuration
public class DataSourceConfig {

    @Value("${review.data-dir:./.review-data}")
    private String dataDir;

    @Bean
    public DataSource dataSource() throws Exception {
        var dir = new File(dataDir);
        if (!dir.exists()) dir.mkdirs();
        var dbFile = new File(dir, "review.db");
        var ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        // Apply all migrations from classpath in order (V1, V2, ...)
        try (var c = ds.getConnection(); var s = c.createStatement()) {
            applyMigration(s, "/db/migration/V1__init.sql");
            applyV2Idempotent(c);
        }
        return ds;
    }

    private static void applyMigration(java.sql.Statement s, String classpathPath) throws Exception {
        try (InputStream is = DataSourceConfig.class.getResourceAsStream(classpathPath)) {
            if (is == null) throw new IllegalStateException(classpathPath + " not found on classpath");
            var sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            for (var stmt : sql.split(";")) {
                var trimmed = stmt.trim();
                if (!trimmed.isEmpty()) s.execute(trimmed);
            }
        }
    }

    /**
     * V2 adds columns and an index. SQLite has no ADD COLUMN IF NOT EXISTS,
     * so we check pragma_table_info before each ALTER.
     */
    private static void applyV2Idempotent(java.sql.Connection c) throws Exception {
        try (var s = c.createStatement()) {
            // Index is idempotent via IF NOT EXISTS
            s.execute("CREATE INDEX IF NOT EXISTS idx_audit_chunk ON audit_events(session_id, chunk_id)");

            addColumnIfMissing(c, "review_sessions", "scan_root", "TEXT");
            addColumnIfMissing(c, "review_sessions", "total_files", "INTEGER");
            addColumnIfMissing(c, "review_sessions", "total_chunks", "INTEGER");
            addColumnIfMissing(c, "audit_events", "chunk_id", "TEXT");
        }
    }

    private static void addColumnIfMissing(java.sql.Connection c, String table, String column, String type) throws Exception {
        if (columnExists(c, table, column)) return;
        try (var s = c.createStatement()) {
            s.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
        }
    }

    private static boolean columnExists(java.sql.Connection c, String table, String column) throws Exception {
        try (var ps = c.prepareStatement("PRAGMA table_info(" + table + ")");
             var rs = ps.executeQuery()) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) return true;
            }
            return false;
        }
    }
}