package com.review.council.config;
import java.util.List;

public record CouncilConfig(
    String name,
    String version,
    int maxRounds,
    String earlyStop,
    List<ReviewerConfig> reviewers,
    AggregatorConfig aggregator,
    HumanGateConfig humanGates,
    FixerConfig fixer,
    ReReviewerConfig reReviewer,
    BudgetConfig budget,
    OutputConfig output
) {
    public static CouncilConfig defaults() {
        return new CouncilConfig(
            "default", "1.0", 3, "no-critical-and-no-major",
            List.of(ReviewerConfig.defaults("architect"), ReviewerConfig.defaults("security")),
            AggregatorConfig.defaults(), HumanGateConfig.defaults(),
            FixerConfig.defaults(), ReReviewerConfig.defaults(),
            BudgetConfig.defaults(), OutputConfig.defaults()
        );
    }
}
