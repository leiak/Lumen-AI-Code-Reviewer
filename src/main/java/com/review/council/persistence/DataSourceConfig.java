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
        // Apply V1 schema from classpath
        try (var c = ds.getConnection(); var s = c.createStatement();
             InputStream is = DataSourceConfig.class.getResourceAsStream("/db/migration/V1__init.sql")) {
            if (is == null) throw new IllegalStateException("V1__init.sql not found on classpath");
            var sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            for (var stmt : sql.split(";")) {
                var trimmed = stmt.trim();
                if (!trimmed.isEmpty()) s.execute(trimmed);
            }
        }
        return ds;
    }
}
