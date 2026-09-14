package com.review.council.gate;

import com.review.council.config.HumanGateConfig;
import com.review.council.state.Finding;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.regex.Pattern;

@Component
public class GateEvaluator {
    private final HumanGateConfig config;

    public GateEvaluator(HumanGateConfig config) {
        this.config = config;
    }

    public List<GateDecision> evaluate(List<Finding> findings, String filePath) {
        return findings.stream()
            .map(f -> evaluateOne(f, filePath))
            .filter(Optional::isPresent).map(Optional::get)
            .toList();
    }

    private Optional<GateDecision> evaluateOne(Finding f, String filePath) {
        var reviewerRules = config.perReviewer().get(f.reviewerRole());
        if (reviewerRules != null) {
            for (var rule : reviewerRules) {
                if (ruleMatches(rule, f)) {
                    return Optional.of(new GateDecision(f, filePath, rule.action(),
                        "per-reviewer." + f.reviewerRole()));
                }
            }
        }
        for (var pr : config.pathRules()) {
            if ("always".equals(pr.gate()) && matchesGlob(pr.pattern(), filePath)) {
                return Optional.of(new GateDecision(f, filePath, "must_approve", "path-rule:" + pr.pattern()));
            }
        }
        return Optional.empty();
    }

    private boolean ruleMatches(HumanGateConfig.Rule rule, Finding f) {
        if ("any".equals(rule.severity())) return true;
        return rule.severity().equals(f.severity());
    }

    private boolean matchesGlob(String pattern, String path) {
        // Convert glob to regex: ** -> .*, ? -> .   BEFORE quoting so wildcards stay as regex
        var regex = pattern.replace("**", ".*").replace("?", ".");
        return path.matches(regex);
    }

    public record GateDecision(Finding finding, String filePath, String action, String ruleMatched) {}
}
