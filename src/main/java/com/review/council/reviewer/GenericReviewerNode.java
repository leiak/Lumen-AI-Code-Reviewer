package com.review.council.reviewer;

import com.review.council.ai.ChatClientRegistry;
import com.review.council.config.PromptTemplate;
import com.review.council.nodes.NodeContext;
import com.review.council.nodes.NodeInputs.ReviewerInput;
import com.review.council.nodes.NodeInputs.ReviewerNode;
import com.review.council.state.CodeDiff;
import com.review.council.state.Finding;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class GenericReviewerNode implements ReviewerNode {
    private final ChatClientRegistry registry;
    private final FindingParser parser;

    public GenericReviewerNode(ChatClientRegistry registry, FindingParser parser) {
        this.registry = registry;
        this.parser = parser;
    }

    @Override public String name() { return "reviewer"; }

    @Override
    public List<Finding> apply(ReviewerInput input, NodeContext ctx) {
        var state = input.state();
        var cfg = input.config();
        var role = cfg.role();
        try {
            var template = PromptTemplate.fromResource(cfg.promptFile());
            var rendered = template.render(Map.of(
                "diff", renderDiff(state.diff()),
                "language", state.language().name().toLowerCase()
            ));
            var client = registry.resolve(cfg.model());
            long start = System.currentTimeMillis();
            var response = client.prompt().user(rendered).call().content();
            long elapsed = System.currentTimeMillis() - start;
            int promptTok = rendered.length() / 4;
            int compTok = response == null ? 0 : response.length() / 4;
            double cost = estimateCost(promptTok, compTok);

            ctx.llmCalls().record(ctx.sessionId(), "reviewer_" + role, cfg.model(),
                promptTok, compTok, cost, elapsed, true, null);
            ctx.audit().record(ctx.sessionId(), "reviewer_" + role, "llm_call",
                String.format("{\"model\":\"%s\",\"tokens\":%d}", cfg.model(), promptTok + compTok));

            var raw = parser.parse(response);
            return raw.stream().map(f -> new Finding(f.id(), role, f.severity(), f.line(), f.message(), f.suggestedFix())).toList();
        } catch (Exception e) {
            ctx.audit().record(ctx.sessionId(), "reviewer_" + role, "error", "{\"error\":\"" + e.getMessage() + "\"}");
            return List.of();
        }
    }

    private String renderDiff(CodeDiff diff) {
        var sb = new StringBuilder();
        for (var f : diff.files()) sb.append(f.newContent());
        return sb.toString();
    }

    private double estimateCost(int pt, int ct) {
        return (pt * 3.0 + ct * 15.0) / 1_000_000;
    }
}
