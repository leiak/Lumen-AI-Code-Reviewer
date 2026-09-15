package com.review.council.scanner;

import com.review.council.state.Finding;
import java.util.List;
import java.util.Map;

public record ScanResult(
    String sessionId,
    String scanRoot,
    int totalFiles,
    int totalChunks,
    List<String> reviewers,
    List<String> files,
    Map<String, PerReviewerSummary> perReviewer,
    List<Finding> findings,
    double cost,
    long durationMs
) {
    public record PerReviewerSummary(
        int findingsCount,
        long tokensIn,
        long tokensOut,
        double costUsd,
        Map<String, Long> bySeverity  // critical / major / minor counts
    ) {}
}