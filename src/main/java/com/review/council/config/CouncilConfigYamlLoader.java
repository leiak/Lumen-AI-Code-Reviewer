package com.review.council.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

@Component
public class CouncilConfigYamlLoader {

    @SuppressWarnings("unchecked")
    public CouncilConfig load(InputStream in) throws IOException {
        var mapper = new ObjectMapper(new YAMLFactory());
        Map<String, Object> root = mapper.readValue(in, Map.class);

        Map<String, Object> council = (Map<String, Object>) root.getOrDefault("council", Map.of());
        Map<String, Object> loop = (Map<String, Object>) root.getOrDefault("loop", Map.of());
        Map<String, Object> agg = (Map<String, Object>) root.getOrDefault("aggregator", Map.of());
        Map<String, Object> fix = (Map<String, Object>) root.getOrDefault("fixer", Map.of());
        Map<String, Object> rer = (Map<String, Object>) root.getOrDefault("re-reviewer", Map.of());
        Map<String, Object> bud = (Map<String, Object>) root.getOrDefault("budget", Map.of());
        Map<String, Object> out = (Map<String, Object>) root.getOrDefault("output", Map.of());

        List<Map<String, Object>> revList = (List<Map<String, Object>>) root.getOrDefault("reviewers", List.of());

        var reviewers = revList.stream().map(r -> new ReviewerConfig(
            (String) r.get("role"),
            !Boolean.FALSE.equals(r.getOrDefault("enabled", true)),
            (String) r.get("model"),
            (String) r.get("prompt-file"),
            (String) r.get("impl"),
            (List<String>) r.getOrDefault("tools", List.of()),
            ((Number) r.getOrDefault("timeout-seconds", 180)).intValue(),
            ((Number) r.getOrDefault("retry-on-error", 1)).intValue()
        )).toList();

        return new CouncilConfig(
            (String) council.getOrDefault("name", "default"),
            (String) council.getOrDefault("version", "1.0"),
            ((Number) loop.getOrDefault("max-rounds", 3)).intValue(),
            (String) loop.getOrDefault("early-stop", "no-critical-and-no-major"),
            reviewers,
            parseAggregator(agg),
            parseHumanGates((Map<String, Object>) root.getOrDefault("human-gates", Map.of())),
            parseFixer(fix),
            parseReReviewer(rer),
            parseBudget(bud),
            parseOutput(out)
        );
    }

    public CouncilConfig loadFromFile(String path) throws IOException {
        try (var in = new java.io.FileInputStream(path)) {
            return load(in);
        }
    }

    private AggregatorConfig parseAggregator(Map<String, Object> m) {
        return new AggregatorConfig(
            (String) m.getOrDefault("strategy", "weighted_vote"),
            Map.of("critical", 3, "major", 2, "minor", 1),
            !Boolean.FALSE.equals(m.getOrDefault("dedup-enabled", true)),
            ((Number) m.getOrDefault("dedup-threshold", 0.7)).doubleValue()
        );
    }

    @SuppressWarnings("unchecked")
    private HumanGateConfig parseHumanGates(Map<String, Object> m) {
        if (m.isEmpty()) return HumanGateConfig.defaults();
        Map<String, Object> perReviewer = (Map<String, Object>) m.getOrDefault("per-reviewer", Map.of());
        java.util.Map<String, List<HumanGateConfig.Rule>> prMap = new java.util.HashMap<>();
        for (var entry : perReviewer.entrySet()) {
            List<Map<String, Object>> rules = (List<Map<String, Object>>) entry.getValue();
            prMap.put(entry.getKey(), rules.stream().map(r ->
                new HumanGateConfig.Rule((String) r.get("severity"), (String) r.get("action"))
            ).toList());
        }
        List<Map<String, Object>> pathList = (List<Map<String, Object>>) m.getOrDefault("path-rules", List.of());
        List<HumanGateConfig.PathRule> pathRules = pathList.stream().map(p ->
            new HumanGateConfig.PathRule((String) p.get("pattern"), (String) p.get("gate"))
        ).toList();
        return new HumanGateConfig((String) m.getOrDefault("default", "never"), prMap, pathRules);
    }

    private FixerConfig parseFixer(Map<String, Object> m) {
        if (m.isEmpty()) return FixerConfig.defaults();
        return new FixerConfig(
            (String) m.getOrDefault("model", "claude-opus-5"),
            (String) m.getOrDefault("prompt-file", "prompts/fixer.md"),
            (String) m.getOrDefault("auto-apply", "minor"),
            ((Number) m.getOrDefault("max-patches-per-round", 20)).intValue(),
            (List<String>) m.getOrDefault("tools", List.of())
        );
    }

    private ReReviewerConfig parseReReviewer(Map<String, Object> m) {
        if (m.isEmpty()) return ReReviewerConfig.defaults();
        return new ReReviewerConfig(
            !Boolean.FALSE.equals(m.getOrDefault("enabled", true)),
            (String) m.getOrDefault("model", "claude-sonnet-5"),
            (String) m.getOrDefault("prompt-file", "prompts/re-review.md"),
            (List<String>) m.getOrDefault("focus", List.of())
        );
    }

    private BudgetConfig parseBudget(Map<String, Object> m) {
        if (m.isEmpty()) return BudgetConfig.defaults();
        return new BudgetConfig(
            ((Number) m.getOrDefault("max-tokens", 500000)).longValue(),
            ((Number) m.getOrDefault("max-time-seconds", 1800)).longValue(),
            new java.math.BigDecimal(m.getOrDefault("max-cost-usd", "5.00").toString()),
            (String) m.getOrDefault("on-exceeded", "pause_and_ask")
        );
    }

    private OutputConfig parseOutput(Map<String, Object> m) {
        if (m.isEmpty()) return OutputConfig.defaults();
        return new OutputConfig(
            (List<String>) m.getOrDefault("format", List.of("markdown")),
            !Boolean.FALSE.equals(m.getOrDefault("include-applied-diffs", true)),
            !Boolean.FALSE.equals(m.getOrDefault("include-failed-attempts", true)),
            (String) m.getOrDefault("save-to", ".review-history/")
        );
    }
}
