package com.review.council.scanner;

import java.util.List;

public record ScanOptions(
    String path,
    int maxTokens,
    int concurrency,
    List<String> includePatterns,
    List<String> excludePatterns,
    List<String> excludePathPatterns,
    int maxFileSizeBytes
) {
    public static ScanOptions defaults() {
        return new ScanOptions(".", 50_000, 3,
            List.of("*.java", "*.kt", "*.py", "*.ts", "*.tsx", "*.js", "*.jsx",
                    "*.go", "*.rs", "*.rb", "*.cs", "*.cpp", "*.h", "*.hpp", "*.swift"),
            List.of(),
            List.of("target/**", "build/**", "dist/**", "node_modules/**", ".git/**",
                    "*.min.*", "generated/**"),
            500_000);
    }

    public ScanOptions withPath(String newPath) {
        return new ScanOptions(newPath, maxTokens, concurrency,
            includePatterns, excludePatterns, excludePathPatterns, maxFileSizeBytes);
    }

    public ScanOptions withMaxFileSizeBytes(int bytes) {
        return new ScanOptions(path, maxTokens, concurrency,
            includePatterns, excludePatterns, excludePathPatterns, bytes);
    }
}