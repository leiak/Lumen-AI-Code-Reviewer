package com.review.council.nodes;

import com.review.council.config.FixerConfig;
import com.review.council.config.ReReviewerConfig;
import com.review.council.config.ReviewerConfig;
import com.review.council.state.Patch;
import com.review.council.state.ReviewState;

import java.util.List;

public final class NodeInputs {
    private NodeInputs() {}

    public record ReviewerInput(ReviewState state, ReviewerConfig config) {}
    public record FixerInput(ReviewState state, FixerConfig config) {}
    public record ReReviewerInput(ReviewState state, ReReviewerConfig config, List<Patch> appliedPatches) {}
    public record LoadDiffInput(ReviewState state, String repoPath, String base, String head) {}
    public record GateEvaluatorInput(ReviewState state) {}
    public record AggregatorInput(ReviewState state) {}
    public record ApplyPatchesInput(ReviewState state) {}

    public interface ReviewerNode extends CouncilNode<ReviewerInput, List<com.review.council.state.Finding>> {}
    public interface FixerNode extends CouncilNode<FixerInput, List<Patch>> {}
    public interface ReReviewerNode extends CouncilNode<ReReviewerInput, List<com.review.council.state.Finding>> {}
}
