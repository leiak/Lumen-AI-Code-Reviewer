package com.review.council.graph;

import com.review.council.config.CouncilConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Structured topology model for the /graph UI (v1.1).
 *
 * <p>The raw LangGraph4j mermaid template is messy:
 * <ul>
 *   <li>It emits commented duplicate edges (e.g. {@code gate -.-> __END__} followed by {@code %% gate -.-> __END__}).</li>
 *   <li>{@code __START__ / __END__} don't get a {@code classDef} so they render as plain black circles.</li>
 *   <li>There's no per-node metadata (role, status) and no legend.</li>
 * </ul>
 *
 * <p>This builder produces a clean, typed model that the new UI renders directly.
 * The current StateGraph topology (v1.0) is fixed:
 * <pre>
 *   START → dispatch → aggregator → gate ⇢ fixer → re_reviewer → gate …
 *                                  ↘ END (when signal != APPLY)
 * </pre>
 *
 * <p>Future iterations can derive this from {@code StateGraph.getNodes()} /
 * {@code getEdges()} — for now we hand-author to keep the JSON stable.
 */
public final class CouncilGraphTopology {

    private CouncilGraphTopology() {}

    public static Map<String, Object> build(CouncilConfig config) {
        Map<String, Object> root = new LinkedHashMap<>();

        root.put("title", config.name());
        root.put("layout", "TB"); // top-bottom; UI lets user switch to LR/BT/RL
        root.put("generatedAt", java.time.Instant.now().toString());
        root.put("version", "v1.1");

        // ---------- Nodes ----------
        List<Map<String, Object>> nodes = new ArrayList<>();
        nodes.add(node("__START__", "Start", "start",
            "Entry point of the StateGraph. Carries sessionId + diff.", "#1e3a8a"));
        nodes.add(node("dispatch", "dispatch", "fanout",
            "并行扇出到所有 reviewer,合并 findings。", "#3b82f6"));
        nodes.add(node("aggregator", "aggregator", "process",
            "Dedup + 按 severity 计数,产 signal(CONTINUE/DONE)。", "#0891b2"));
        nodes.add(node("gate", "gate", "decision",
            "GateEvaluator: 决定 signal=APPLY(进 fixer) 或 END(收尾)。", "#f59e0b"));
        nodes.add(node("fixer", "fixer", "process",
            "基于 findings 生成 patch 并尝试落地;产出 applied/failed。", "#7c3aed"));
        nodes.add(node("re_reviewer", "re_reviewer", "review",
            "对已 applied 的 patch 重新跑一遍 reviewer 闭环。", "#06b6d4"));
        nodes.add(node("__END__", "End", "end",
            "图执行结束,所有 round 结束或 signal=DONE 时进入。", "#dc2626"));
        root.put("nodes", nodes);

        // ---------- Edges ----------
        List<Map<String, Object>> edges = new ArrayList<>();
        edges.add(edge("__START__",   "dispatch",      "solid", "init"));
        edges.add(edge("dispatch",    "aggregator",    "solid", String.valueOf(config.reviewers() != null ? config.reviewers().size() : 0) + " reviewers"));
        edges.add(edge("aggregator",  "gate",          "solid", "findings"));
        edges.add(edge("gate",        "fixer",         "conditional", "signal=APPLY"));
        edges.add(edge("gate",        "__END__",       "conditional", "signal≠APPLY"));
        edges.add(edge("fixer",       "re_reviewer",   "solid", "patches"));
        edges.add(edge("re_reviewer", "gate",          "solid", "round+" + 1));
        root.put("edges", edges);

        // ---------- Legend ----------
        List<Map<String, Object>> legend = new ArrayList<>();
        legend.add(legend("start",     "入口 / 出口",  "#1e3a8a"));
        legend.add(legend("end",       "终止节点",     "#dc2626"));
        legend.add(legend("fanout",    "并行扇出",     "#3b82f6"));
        legend.add(legend("process",   "业务处理",     "#06b6d4"));
        legend.add(legend("decision",  "条件路由",     "#f59e0b"));
        legend.add(legend("review",    "LLM 评审",     "#7c3aed"));
        root.put("legend", legend);

        // ---------- Stats ----------
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("nodeCount", nodes.size());
        stats.put("edgeCount", edges.size());
        stats.put("reviewerCount", config.reviewers() != null ? config.reviewers().size() : 0);
        stats.put("maxRounds", config.maxRounds());
        stats.put("conditionalEdges", edges.stream().filter(e -> "conditional".equals(e.get("kind"))).count());
        root.put("stats", stats);

        return root;
    }

    private static Map<String, Object> node(String id, String label, String type,
                                             String role, String color) {
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("id", id);
        n.put("label", label);
        n.put("type", type);     // start / end / fanout / process / decision / review
        n.put("role", role);
        n.put("color", color);
        return n;
    }

    private static Map<String, Object> edge(String from, String to, String kind, String label) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("from", from);
        e.put("to", to);
        e.put("kind", kind);    // solid | conditional
        e.put("label", label);
        return e;
    }

    private static Map<String, Object> legend(String type, String label, String color) {
        Map<String, Object> l = new LinkedHashMap<>();
        l.put("type", type);
        l.put("label", label);
        l.put("color", color);
        return l;
    }
}
