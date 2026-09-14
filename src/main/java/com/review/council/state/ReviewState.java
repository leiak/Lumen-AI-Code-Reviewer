package com.review.council.state;
import com.review.council.config.CouncilConfig;
import java.util.List;

public record ReviewState(
    String sessionId,
    CodeDiff diff,
    Language language,
    CouncilConfig config,
    List<Finding> findings,
    List<Patch> proposedPatches,
    List<Patch> appliedPatches,
    int round,
    Budget budget,
    FlowSignal signal
) {
    public ReviewState withFindingsAdded(List<Finding> more) {
        return new ReviewState(sessionId, diff, language, config,
            concat(findings, more), proposedPatches, appliedPatches, round, budget, signal);
    }
    public ReviewState withProposedPatches(List<Patch> p) {
        return new ReviewState(sessionId, diff, language, config,
            findings, concat(proposedPatches, p), appliedPatches, round, budget, signal);
    }
    public ReviewState withAppliedPatches(List<Patch> p) {
        return new ReviewState(sessionId, diff, language, config,
            findings, proposedPatches, concat(appliedPatches, p), round, budget, signal);
    }
    public ReviewState withRound(int r) {
        return new ReviewState(sessionId, diff, language, config,
            findings, proposedPatches, appliedPatches, r, budget, signal);
    }
    public ReviewState withBudget(Budget b) {
        return new ReviewState(sessionId, diff, language, config,
            findings, proposedPatches, appliedPatches, round, b, signal);
    }
    public ReviewState withSignal(FlowSignal s) {
        return new ReviewState(sessionId, diff, language, config,
            findings, proposedPatches, appliedPatches, round, budget, s);
    }
    public ReviewState withDiff(CodeDiff d) {
        return new ReviewState(sessionId, d, language, config,
            findings, proposedPatches, appliedPatches, round, budget, signal);
    }
    private static <T> List<T> concat(List<T> a, List<T> b) {
        var r = new java.util.ArrayList<>(a); r.addAll(b); return List.copyOf(r);
    }
}
