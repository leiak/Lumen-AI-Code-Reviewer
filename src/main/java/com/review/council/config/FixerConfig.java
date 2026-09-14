package com.review.council.config;
import java.util.List;
public record FixerConfig(
    String model,
    String promptFile,
    String autoApply,
    int maxPatchesPerRound,
    List<String> tools
) {
    public static FixerConfig defaults() {
        return new FixerConfig("claude-opus-5", "prompts/fixer.md", "minor", 20,
            List.of("git_apply", "test_runner"));
    }
}
