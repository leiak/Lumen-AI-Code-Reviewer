package com.review.council.gate;

import com.review.council.nodes.CouncilNode;
import com.review.council.nodes.NodeContext;
import com.review.council.nodes.NodeInputs.GateEvaluatorInput;
import com.review.council.state.FlowSignal;
import com.review.council.state.ReviewState;
import org.springframework.stereotype.Component;

@Component
public class GateEvaluatorNode implements CouncilNode<GateEvaluatorInput, ReviewState> {
    private final GateEvaluator evaluator;

    public GateEvaluatorNode(GateEvaluator evaluator) { this.evaluator = evaluator; }

    @Override public String name() { return "gate_evaluator"; }

    @Override
    public ReviewState apply(GateEvaluatorInput input, NodeContext ctx) {
        var state = input.state();
        var filePaths = state.diff().files().stream().map(f -> f.path()).toList();
        boolean anyGate = false;
        for (var path : filePaths) {
            var decisions = evaluator.evaluate(state.findings(), path);
            if (!decisions.isEmpty()) anyGate = true;
            for (var d : decisions) {
                ctx.audit().record(ctx.sessionId(), "gate_evaluator", "gate_pending",
                    String.format("{\"finding\":\"%s\",\"action\":\"%s\",\"rule\":\"%s\"}",
                        d.finding().id(), d.action(), d.ruleMatched()));
            }
        }
        return state.withSignal(anyGate ? FlowSignal.WAIT_HUMAN : FlowSignal.CONTINUE);
    }
}
