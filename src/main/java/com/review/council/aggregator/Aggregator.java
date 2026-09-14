package com.review.council.aggregator;

import com.review.council.config.AggregatorConfig;
import com.review.council.state.Finding;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class Aggregator {
    private final AggregatorConfig config;

    public Aggregator(AggregatorConfig config) {
        this.config = config;
    }

    public List<Finding> dedup(List<Finding> findings) {
        if (!config.dedupEnabled() || findings.size() <= 1) return findings;
        var result = new ArrayList<Finding>();
        for (var f : findings) {
            boolean merged = false;
            for (int i = 0; i < result.size(); i++) {
                if (jaccard(f.message(), result.get(i).message()) >= config.dedupThreshold()) {
                    if (severityRank(f.severity()) > severityRank(result.get(i).severity())) {
                        result.set(i, f);
                    }
                    merged = true;
                    break;
                }
            }
            if (!merged) result.add(f);
        }
        return result;
    }

    public Map<String, Integer> countsBySeverity(List<Finding> findings) {
        var m = new LinkedHashMap<String, Integer>();
        m.put("critical", 0); m.put("major", 0); m.put("minor", 0);
        for (var f : findings) {
            m.merge(f.severity(), 1, Integer::sum);
        }
        return m;
    }

    private static int severityRank(String s) {
        return switch (s) {
            case "critical" -> 3;
            case "major" -> 2;
            case "minor" -> 1;
            default -> 0;
        };
    }

    private static double jaccard(String a, String b) {
        var sa = new HashSet<>(Arrays.asList(a.toLowerCase().split("\\s+")));
        var sb = new HashSet<>(Arrays.asList(b.toLowerCase().split("\\s+")));
        var inter = new HashSet<>(sa); inter.retainAll(sb);
        var union = new HashSet<>(sa); union.addAll(sb);
        return union.isEmpty() ? 0 : (double) inter.size() / union.size();
    }
}
