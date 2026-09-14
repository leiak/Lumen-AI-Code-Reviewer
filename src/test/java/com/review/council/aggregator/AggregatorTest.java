package com.review.council.aggregator;

import com.review.council.config.AggregatorConfig;
import com.review.council.state.Finding;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class AggregatorTest {
    private final Aggregator agg = new Aggregator(AggregatorConfig.defaults());

    @Test
    void dedupes_similarFindings() {
        var f1 = Finding.of("architect", "major", 10, "N+1 query in loop", "use batch");
        var f2 = Finding.of("performance", "major", 12, "N+1 query in loop iteration", "use batch");
        var deduped = agg.dedup(List.of(f1, f2));
        assertThat(deduped).hasSize(1);
    }

    @Test
    void keeps_distinct_findings() {
        var f1 = Finding.of("architect", "major", 10, "N+1 query", "fix1");
        var f2 = Finding.of("security", "critical", 5, "SQL injection", "fix2");
        var deduped = agg.dedup(List.of(f1, f2));
        assertThat(deduped).hasSize(2);
    }

    @Test
    void counts_by_severity() {
        var f1 = Finding.of("a", "critical", 1, "x", "");
        var f2 = Finding.of("b", "critical", 2, "y", "");
        var f3 = Finding.of("c", "minor", 3, "z", "");
        var counts = agg.countsBySeverity(List.of(f1, f2, f3));
        assertThat(counts).containsEntry("critical", 2).containsEntry("minor", 1);
    }
}
