package com.review.council.config;
import java.util.List;
public record OutputConfig(
    List<String> format,
    boolean includeAppliedDiffs,
    boolean includeFailedAttempts,
    String saveTo
) {
    public static OutputConfig defaults() {
        return new OutputConfig(List.of("markdown", "json"), true, true, ".review-history/");
    }
}
