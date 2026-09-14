package com.review.council.config;
import java.util.List;
public record ReReviewerConfig(
    boolean enabled,
    String model,
    String promptFile,
    List<String> focus
) {
    public static ReReviewerConfig defaults() {
        return new ReReviewerConfig(true, "claude-sonnet-5", "prompts/re-review.md",
            List.of("regression", "test_pass", "style_drift"));
    }
}
