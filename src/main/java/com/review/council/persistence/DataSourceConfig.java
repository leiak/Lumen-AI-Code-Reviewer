package com.review.council.persistence;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.sqlite.SQLiteDataSource;
import javax.sql.DataSource;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

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
        // Apply V1 schema (simple migration; full Flyway in v1.1)
        try (var c = ds.getConnection(); var s = c.createStatement()) {
            var sql = new String(Files.readAllBytes(Path.of("src/main/resources/db/migration/V1__init.sql")));
            for (var stmt : sql.split(";")) {
                var trimmed = stmt.trim();
                if (!trimmed.isEmpty()) s.execute(trimmed);
            }
        }
        return ds;
    }
}
