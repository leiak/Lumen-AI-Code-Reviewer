package com.review.council.state;

public record Finding(
    String id,
    String reviewerRole,
    String severity,
    int line,
    String message,
    String suggestedFix,
    String chunkId,    // NEW: null for diff-based sessions
    String filePath    // NEW: file the finding belongs to
) {
    /** Backward-compatible factory for diff-based reviewers (chunkId=null, filePath=null). */
    public static Finding of(String role, String severity, int line, String msg, String fix) {
        return new Finding(java.util.UUID.randomUUID().toString(),
            role, severity, line, msg, fix, null, null);
    }

    /** New factory for scan-based reviewers carrying chunk + file context. */
    public static Finding of(String role, String severity, int line, String msg,
                             String fix, String chunkId, String filePath) {
        return new Finding(java.util.UUID.randomUUID().toString(),
            role, severity, line, msg, fix, chunkId, filePath);
    }
}