package com.review.council.config;
import java.util.Map;
public record AggregatorConfig(
    String strategy,
    Map<String, Integer> weights,
    boolean dedupEnabled,
    double dedupThreshold
) {
    public static AggregatorConfig defaults() {
        return new AggregatorConfig("weighted_vote",
            Map.of("critical", 3, "major", 2, "minor", 1), true, 0.7);
    }
}
