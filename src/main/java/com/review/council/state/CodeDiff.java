package com.review.council.state;
import java.util.List;

public record CodeDiff(
    String baseRef,
    String headRef,
    List<FileDiff> files
) {
    public record FileDiff(String path, String oldContent, String newContent, List<Hunk> hunks) {}
    public record Hunk(int oldStart, int oldLines, int newStart, int newLines, String content) {}
}
