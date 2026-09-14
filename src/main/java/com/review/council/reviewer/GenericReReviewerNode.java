package com.review.council.reviewer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.review.council.ai.ChatClientRegistry;
import com.review.council.config.PromptTemplate;
import com.review.council.nodes.NodeContext;
import com.review.council.nodes.NodeInputs.ReReviewerInput;
import com.review.council.nodes.NodeInputs.ReReviewerNode;
import com.review.council.state.CodeDiff;
import com.review.council.state.Finding;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class GenericReReviewerNode implements ReReviewerNode {
    private final ChatClientRegistry registry;
    private final FindingParser parser;
    private final ObjectMapper mapper = new ObjectMapper();

    public GenericReReviewerNode(ChatClientRegistry registry, FindingParser parser) {
        this.registry = registry; this.parser = parser;
    }
    @Override public String name() { return "re_reviewer"; }

    @Override
    public List<Finding> apply(ReReviewerInput input, NodeContext ctx) {
        var state = input.state();
        var cfg = input.config();
        try {
            var template = PromptTemplate.fromResource(cfg.promptFile());
            var patchesJson = mapper.writeValueAsString(input.appliedPatches());
            var rendered = template.render(Map.of(
                "diff", renderDiff(state.diff()),
                "appliedPatches", patchesJson,
                "focus", String.join(",", cfg.focus())
            ));
            var client = registry.resolve(cfg.model());
            long start = System.currentTimeMillis();
            var response = client.prompt().user(rendered).call().content();
            long elapsed = System.currentTimeMillis() - start;

            ctx.llmCalls().record(ctx.sessionId(), "re_reviewer", cfg.model(),
                rendered.length() / 4, response.length() / 4, 0.05, elapsed, true, null);
            ctx.audit().record(ctx.sessionId(), "re_reviewer", "llm_call",
                "{\"model\":\"" + cfg.model() + "\"}");

            var raw = parser.parse(response);
            return raw.stream().map(f -> new Finding(f.id(), "re_review", f.severity(), f.line(), f.message(), f.suggestedFix())).toList();
        } catch (Exception e) {
            ctx.audit().record(ctx.sessionId(), "re_reviewer", "error", "{\"error\":\"" + e.getMessage() + "\"}");
            return List.of();
        }
    }

    private String renderDiff(CodeDiff d) {
        var sb = new StringBuilder();
        for (var f : d.files()) sb.append(f.newContent());
        return sb.toString();
    }
}
