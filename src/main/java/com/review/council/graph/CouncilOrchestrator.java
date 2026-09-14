package com.review.council.graph;

import com.review.council.aggregator.Aggregator;
import com.review.council.audit.AuditRepository;
import com.review.council.audit.LlmCallRepository;
import com.review.council.config.CouncilConfig;
import com.review.council.fixer.GenericFixerNode;
import com.review.council.gate.GateEvaluatorNode;
import com.review.council.nodes.NodeContext;
import com.review.council.nodes.NodeInputs;
import com.review.council.persistence.SessionRepository;
import com.review.council.persistence.StateSnapshotRepository;
import com.review.council.reviewer.GenericReReviewerNode;
import com.review.council.reviewer.GenericReviewerNode;
import com.review.council.state.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.*;

/**
 * Direct orchestrator (MVP). Ties together: parallel reviewers → aggregator →
 * gate_evaluator → fixer → re_reviewer loop. Uses LangGraph4j concepts
 * (state graph topology, signal-based routing) but executes linearly for clarity.
 * v1.1: replace with explicit StateGraph + Send API for visual debugging.
 */
@Component
public class CouncilOrchestrator {

    private final GenericReviewerNode reviewer;
    private final Aggregator aggregator;
    private final GateEvaluatorNode gateNode;
    private final GenericFixerNode fixer;
    private final GenericReReviewerNode reReviewer;
    private final SessionRepository sessions;
    private final StateSnapshotRepository snapshots;
    private final AuditRepository audit;
    private final LlmCallRepository llmCalls;
    private final ObjectMapper mapper = new ObjectMapper();

    public CouncilOrchestrator(GenericReviewerNode reviewer, Aggregator aggregator,
                                GateEvaluatorNode gateNode, GenericFixerNode fixer,
                                GenericReReviewerNode reReviewer,
                                SessionRepository sessions, StateSnapshotRepository snapshots,
                                AuditRepository audit, LlmCallRepository llmCalls) {
        this.reviewer = reviewer; this.aggregator = aggregator; this.gateNode = gateNode;
        this.fixer = fixer; this.reReviewer = reReviewer;
        this.sessions = sessions; this.snapshots = snapshots;
        this.audit = audit; this.llmCalls = llmCalls;
    }

    public ReviewState run(ReviewState initial) {
        var config = initial.config();
        var ctx = new NodeContext(initial.sessionId(), audit, llmCalls, snapshots,
            initial.budget(), Map.of());

        // Round 0: parallel reviewers
        ReviewState state = initial;
        for (int round = 0; round < config.maxRounds(); round++) {
            audit.record(state.sessionId(), "orchestrator", "round_start",
                String.format("{\"round\":%d}", round));

            // Parallel reviewers (sequential here for MVP; parallel in v1.1 with LangGraph4j Send)
            List<Finding> roundFindings = new ArrayList<>();
            for (var rc : config.reviewers()) {
                if (!rc.enabled()) continue;
                var findings = reviewer.apply(new NodeInputs.ReviewerInput(state, rc), ctx);
                roundFindings.addAll(findings);
            }

            // Snapshot after reviewers
            try {
                snapshots.save(state.sessionId(), "reviewers", round,
                    mapper.writeValueAsString(state.withFindingsAdded(roundFindings)));
            } catch (Exception ignored) {}

            // Aggregate (dedup)
            state = state.withFindingsAdded(roundFindings);
            var dedupedFindings = aggregator.dedup(state.findings());
            state = new ReviewState(state.sessionId(), state.diff(), state.language(), state.config(),
                dedupedFindings, state.proposedPatches(), state.appliedPatches(),
                state.round(), state.budget(), state.signal());

            // Early stop check
            if (dedupedFindings.isEmpty()) {
                audit.record(state.sessionId(), "orchestrator", "early_stop", "{\"reason\":\"no_findings\"}");
                break;
            }
            if (round > 0 && config.earlyStop().equals("no-critical-and-no-major")) {
                var counts = aggregator.countsBySeverity(dedupedFindings);
                if (counts.get("critical") == 0 && counts.get("major") == 0) break;
            }

            // GateEvaluator (sets signal)
            state = gateNode.apply(new NodeInputs.GateEvaluatorInput(state), ctx);

            // Fixer
            var patches = fixer.apply(new NodeInputs.FixerInput(state, config.fixer()), ctx);
            // Apply patches (best-effort in MVP; worktree isolation in v1.1)
            List<Patch> applied = new ArrayList<>();
            for (var p : patches) {
                try {
                    var path = java.nio.file.Paths.get(p.filePath());
                    if (com.review.council.fixer.PatchApplier.applyReplace(path, p.oldText(), p.newText())) {
                        applied.add(p.withStatus("applied"));
                    } else {
                        applied.add(p.withStatus("failed"));
                    }
                } catch (Exception e) {
                    applied.add(p.withStatus("failed"));
                }
            }
            state = state.withProposedPatches(patches).withAppliedPatches(applied);

            // ReReviewer
            var newFindings = reReviewer.apply(
                new NodeInputs.ReReviewerInput(state, config.reReviewer(), applied), ctx);

            state = state.withFindingsAdded(newFindings).withRound(round + 1);

            // Snapshot after full round
            try {
                snapshots.save(state.sessionId(), "round_complete", round,
                    mapper.writeValueAsString(state));
            } catch (Exception ignored) {}
        }

        state = state.withSignal(FlowSignal.DONE);
        sessions.updateStatus(state.sessionId(), "completed", "done");
        return state;
    }

    public BigDecimal totalCost(String sessionId) {
        return BigDecimal.valueOf(llmCalls.totalCostFor(sessionId));
    }
}
