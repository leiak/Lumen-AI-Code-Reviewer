package com.review.council.config;
import java.util.List;
public record ReviewerConfig(
    String role,
    boolean enabled,
    String model,
    String promptFile,
    String impl,
    List<String> tools,
    int timeoutSeconds,
    int retryOnError
) {
    public static ReviewerConfig defaults(String role) {
        return new ReviewerConfig(role, true, "claude-sonnet-5", "prompts/" + role + ".md",
            null, List.of(), 180, 1);
    }
}
