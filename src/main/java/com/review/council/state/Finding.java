package com.review.council.state;

public record Finding(
    String id,
    String reviewerRole,
    String severity,
    int line,
    String message,
    String suggestedFix
) {
    public static Finding of(String role, String severity, int line, String msg, String fix) {
        return new Finding(java.util.UUID.randomUUID().toString(), role, severity, line, msg, fix);
    }
}
