package com.review.council.fixer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.review.council.ai.ChatClientRegistry;
import com.review.council.config.PromptTemplate;
import com.review.council.nodes.NodeContext;
import com.review.council.nodes.NodeInputs.FixerInput;
import com.review.council.nodes.NodeInputs.FixerNode;
import com.review.council.state.CodeDiff;
import com.review.council.state.Patch;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class GenericFixerNode implements FixerNode {
    private final ChatClientRegistry registry;
    private final ObjectMapper mapper = new ObjectMapper();

    public GenericFixerNode(ChatClientRegistry registry) { this.registry = registry; }
    @Override public String name() { return "fixer"; }

    @Override
    public List<Patch> apply(FixerInput input, NodeContext ctx) {
        var state = input.state();
        var cfg = input.config();
        try {
            var template = PromptTemplate.fromResource(cfg.promptFile());
            var findingsJson = mapper.writeValueAsString(state.findings());
            var rendered = template.render(Map.of(
                "diff", renderDiff(state.diff()),
                "previousFindings", findingsJson
            ));
            var client = registry.resolve(cfg.model());
            long start = System.currentTimeMillis();
            var response = client.prompt().user(rendered).call().content();
            long elapsed = System.currentTimeMillis() - start;

            ctx.llmCalls().record(ctx.sessionId(), "fixer", cfg.model(),
                rendered.length() / 4, response.length() / 4, 0.10, elapsed, true, null);
            ctx.audit().record(ctx.sessionId(), "fixer", "llm_call",
                "{\"model\":\"" + cfg.model() + "\"}");

            return parsePatches(response);
        } catch (Exception e) {
            ctx.audit().record(ctx.sessionId(), "fixer", "error", "{\"error\":\"" + e.getMessage() + "\"}");
            return List.of();
        }
    }

    private String renderDiff(CodeDiff d) {
        var sb = new StringBuilder();
        for (var f : d.files()) sb.append(f.newContent());
        return sb.toString();
    }

    private List<Patch> parsePatches(String json) throws Exception {
        var cleaned = json.replaceAll("(?s)```json\\s*", "").replaceAll("(?s)```", "").trim();
        var tree = mapper.readTree(cleaned);
        var arr = tree.get("patches");
        if (arr == null || !arr.isArray()) return List.of();
        List<Patch> out = new ArrayList<>();
        for (var n : arr) {
            out.add(Patch.proposed(n.get("file").asText(), n.get("old").asText(), n.get("new").asText()));
        }
        return out;
    }
}
