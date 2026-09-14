package com.review.council.graph;

import com.review.council.aggregator.Aggregator;
import com.review.council.audit.AuditRepository;
import com.review.council.audit.LlmCallRepository;
import com.review.council.config.CouncilConfig;
import com.review.council.fixer.GenericFixerNode;
import com.review.council.gate.GateEvaluatorNode;
import com.review.council.nodes.NodeContext;
import com.review.council.nodes.NodeInputs;
import com.review.council.persistence.StateSnapshotRepository;
import com.review.council.reviewer.GenericReReviewerNode;
import com.review.council.reviewer.GenericReviewerNode;
import com.review.council.state.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.AgentStateFactory;
import org.bsc.langgraph4j.state.Channel;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static java.util.concurrent.CompletableFuture.supplyAsync;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.bsc.langgraph4j.state.Channels.appender;
import static org.bsc.langgraph4j.state.Channels.base;

/**
 * Real LangGraph4j StateGraph. Topology:
 *
 *   START → dispatch ─→ aggregator → gate ─→ fixer → re_reviewer ─┐
 *                          ↑            │                            │
 *                          │            └─ DONE (no findings)         │
 *                          └──────────────────────────────────────────┘
 *
 * dispatch fans out to all configured reviewers in parallel (Send-style via
 * ExecutorService inside a single AsyncNodeAction). Conditional edges route
 * based on `signal` (CONTINUE / APPLY / DONE) and `round` (vs maxRounds).
 */
@Component
public class CouncilStateGraphRunner {

    private final GenericReviewerNode reviewer;
    private final GenericReReviewerNode reReviewer;
    private final Aggregator aggregator;
    private final GateEvaluatorNode gateNode;
    private final GenericFixerNode fixer;
    private final AuditRepository audit;
    private final LlmCallRepository llmCalls;
    private final StateSnapshotRepository snapshots;
    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private final ObjectMapper mapper = new ObjectMapper();

    public CouncilStateGraphRunner(GenericReviewerNode reviewer, GenericReReviewerNode reReviewer,
                                    Aggregator aggregator, GateEvaluatorNode gateNode, GenericFixerNode fixer,
                                    AuditRepository audit, LlmCallRepository llmCalls,
                                    StateSnapshotRepository snapshots) {
        this.reviewer = reviewer; this.reReviewer = reReviewer; this.aggregator = aggregator;
        this.gateNode = gateNode; this.fixer = fixer; this.audit = audit;
        this.llmCalls = llmCalls; this.snapshots = snapshots;
    }

    /** Builds (does not compile) the StateGraph for topology inspection. */
    public StateGraph<State> defineGraph(CouncilConfig config) throws Exception {
        return defineGraphInternal(config, false);
    }

    public CompiledGraph<State> build(CouncilConfig config) throws Exception {
        return defineGraphInternal(config, true).compile();
    }

    private StateGraph<State> defineGraphInternal(CouncilConfig config, boolean compile) throws Exception {
        Map<String, Channel<?>> channels = Map.of(
            "sessionId", base(() -> ""),
            "round", base(() -> 0),
            "maxRounds", base(() -> config.maxRounds()),
            "findings", appender(() -> new ArrayList<Finding>()),
            "patches", appender(() -> new ArrayList<Patch>()),
            "applied", appender(() -> new ArrayList<Patch>()),
            "signal", base(() -> FlowSignal.CONTINUE.name()),
            "log", appender(() -> new ArrayList<String>())
        );

        AgentStateFactory<State> factory = State::new;

        StateGraph<State> graph = new StateGraph<>(channels, factory);

        // 1. dispatch: fan-out parallel reviewers
        graph.addNode("dispatch", node_async(state -> {
            int round = state.<Integer>value("round").orElse(0);
            String sid = state.<String>value("sessionId").orElse("");
            int maxRounds = state.<Integer>value("maxRounds").orElse(config.maxRounds());

            if (round >= maxRounds) {
                return Map.of("signal", FlowSignal.DONE.name());
            }

            var ctx = new NodeContext(sid, audit, llmCalls, snapshots, null, Map.of());
            var rs = config.reviewers();
            var stateAsReviewInput = new ReviewState(sid, new CodeDiff("HEAD", "HEAD", List.of()),
                Language.JAVA, config, List.of(), List.of(), List.of(), round,
                Budget.empty(0, 0, java.math.BigDecimal.ZERO), FlowSignal.CONTINUE);

            // Parallel via ExecutorService
            var futures = new ArrayList<Future<List<Finding>>>();
            for (var rc : rs) {
                if (!rc.enabled()) continue;
                futures.add(pool.submit(() -> reviewer.apply(
                    new NodeInputs.ReviewerInput(stateAsReviewInput, rc), ctx)));
            }
            var all = new ArrayList<Finding>();
            for (var f : futures) all.addAll(f.get());
            return Map.of("findings", all, "signal", FlowSignal.CONTINUE.name());
        }));

        // 2. aggregator: dedup
        graph.addNode("aggregator", node_async(state -> {
            @SuppressWarnings("unchecked")
            var raw = (List<Finding>) state.<Object>value("findings").orElse(List.of());
            var deduped = aggregator.dedup(raw);
            var counts = aggregator.countsBySeverity(deduped);
            String sig = deduped.isEmpty() ? FlowSignal.DONE.name() : FlowSignal.CONTINUE.name();
            return Map.of("findings", deduped, "signal", sig,
                "log", List.of("aggregator:" + counts));
        }));

        // 3. gate: classify (placeholder signal; full evaluator in dedicated node)
        graph.addNode("gate", node_async(state -> {
            int round = state.<Integer>value("round").orElse(0);
            @SuppressWarnings("unchecked")
            var findings = (List<Finding>) state.<Object>value("findings").orElse(List.of());
            String sid = state.<String>value("sessionId").orElse("");
            var ctx = new NodeContext(sid, audit, llmCalls, snapshots, null, Map.of());
            var configLocal = config;
            var rs = new ReviewState(sid, new CodeDiff("HEAD", "HEAD", List.of()),
                Language.JAVA, configLocal, findings, List.of(), List.of(), round,
                Budget.empty(0, 0, java.math.BigDecimal.ZERO), FlowSignal.CONTINUE);
            var after = gateNode.apply(new NodeInputs.GateEvaluatorInput(rs), ctx);
            return Map.of("signal", after.signal().name());
        }));

        // 4. fixer
        graph.addNode("fixer", node_async(state -> {
            int round = state.<Integer>value("round").orElse(0);
            @SuppressWarnings("unchecked")
            var findings = (List<Finding>) state.<Object>value("findings").orElse(List.of());
            String sid = state.<String>value("sessionId").orElse("");
            var ctx = new NodeContext(sid, audit, llmCalls, snapshots, null, Map.of());
            var rs = new ReviewState(sid, new CodeDiff("HEAD", "HEAD", List.of()),
                Language.JAVA, config, findings, List.of(), List.of(), round,
                Budget.empty(0, 0, java.math.BigDecimal.ZERO), FlowSignal.CONTINUE);
            var patches = fixer.apply(new NodeInputs.FixerInput(rs, config.fixer()), ctx);
            // Apply
            var applied = new ArrayList<Patch>();
            for (var p : patches) {
                try {
                    if (com.review.council.fixer.PatchApplier.applyReplace(
                            java.nio.file.Paths.get(p.filePath()), p.oldText(), p.newText())) {
                        applied.add(p.withStatus("applied"));
                    } else applied.add(p.withStatus("failed"));
                } catch (Exception e) { applied.add(p.withStatus("failed")); }
            }
            return Map.of("patches", patches, "applied", applied);
        }));

        // 5. re-reviewer
        graph.addNode("re_reviewer", node_async(state -> {
            int round = state.<Integer>value("round").orElse(0);
            @SuppressWarnings("unchecked")
            var findings = (List<Finding>) state.<Object>value("findings").orElse(List.of());
            @SuppressWarnings("unchecked")
            var applied = (List<Patch>) state.<Object>value("applied").orElse(List.of());
            String sid = state.<String>value("sessionId").orElse("");
            var ctx = new NodeContext(sid, audit, llmCalls, snapshots, null, Map.of());
            var rs = new ReviewState(sid, new CodeDiff("HEAD", "HEAD", List.of()),
                Language.JAVA, config, findings, List.of(), List.of(), round,
                Budget.empty(0, 0, java.math.BigDecimal.ZERO), FlowSignal.CONTINUE);
            var newFindings = reReviewer.apply(
                new NodeInputs.ReReviewerInput(rs, config.reReviewer(), applied), ctx);
            return Map.of("findings", newFindings, "round", round + 1);
        }));

        // Edges
        graph.addEdge(StateGraph.START, "dispatch");
        graph.addEdge("dispatch", "aggregator");
        graph.addEdge("aggregator", "gate");
        // After gate: if signal is APPLY -> fixer; otherwise -> END
        graph.addConditionalEdges("gate", edge_async(state ->
                state.<String>value("signal").orElse("CONTINUE")
                    .equals("APPLY") ? "fixer" : StateGraph.END),
            Map.of("fixer", "fixer", StateGraph.END, StateGraph.END));
        graph.addEdge("fixer", "re_reviewer");
        graph.addEdge("re_reviewer", "gate");

        return graph;
    }

    public ReviewState run(CouncilConfig config, String sessionId) throws Exception {
        var compiled = build(config);
        var initialMap = Map.<String, Object>of(
            "sessionId", sessionId,
            "round", 0,
            "maxRounds", config.maxRounds(),
            "findings", new ArrayList<Finding>(),
            "patches", new ArrayList<Patch>(),
            "applied", new ArrayList<Patch>(),
            "signal", FlowSignal.CONTINUE.name(),
            "log", new ArrayList<String>()
        );
        var result = compiled.invoke(initialMap).orElseThrow();
        var findings = result.<Object>value("findings")
            .map(v -> (List<Finding>) v).orElse(List.of());
        var applied = result.<Object>value("applied")
            .map(v -> (List<Patch>) v).orElse(List.of());
        var round = result.<Integer>value("round").orElse(0);
        return new ReviewState(sessionId, new CodeDiff("HEAD", "HEAD", List.of()),
            Language.JAVA, config, findings, List.of(), applied, round,
            Budget.empty(0, 0, java.math.BigDecimal.ZERO), FlowSignal.DONE);
    }

    public static class State extends AgentState {
        public State(Map<String, Object> data) { super(data); }
    }
}
