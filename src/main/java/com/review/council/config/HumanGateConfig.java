package com.review.council.config;
import java.util.List;
import java.util.Map;

public record HumanGateConfig(
    String defaultAction,
    Map<String, List<Rule>> perReviewer,
    List<PathRule> pathRules
) {
    public record Rule(String severity, String action) {}
    public record PathRule(String pattern, String gate) {}

    public static HumanGateConfig defaults() {
        return new HumanGateConfig("never", Map.of(), List.of());
    }
}
