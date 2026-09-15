package com.review.council.scanner;

import com.review.council.state.Finding;
import java.util.List;

public record ScanResult(
    String sessionId,
    int totalFiles,
    int totalChunks,
    List<String> reviewers,
    List<Finding> findings,
    double cost,
    long durationMs
) {}