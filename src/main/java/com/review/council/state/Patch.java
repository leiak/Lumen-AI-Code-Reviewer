package com.review.council.state;

public record Patch(
    String id,
    String filePath,
    String oldText,
    String newText,
    String status
) {
    public static Patch proposed(String file, String oldT, String newT) {
        return new Patch(java.util.UUID.randomUUID().toString(), file, oldT, newT, "proposed");
    }
    public Patch withStatus(String s) { return new Patch(id, filePath, oldText, newText, s); }
}
