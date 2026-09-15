package com.review.council.scanner;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

@Component
public class PathScanner {
    private final ScanOptions opts;

    @Autowired
    public PathScanner(ScanOptions opts) {
        this.opts = opts != null ? opts : ScanOptions.defaults();
    }

    public List<FileEntry> scan() throws Exception {
        var root = Path.of(opts.path()).toAbsolutePath();
        if (!Files.exists(root.resolve(".git"))) {
            throw new IllegalStateException("Not a git repo: " + root + ". Run from a git repo root or pass --path=<repo>");
        }
        return gitLsFiles(root).stream()
            .filter(this::matchesInclude)
            .filter(rel -> !matchesExclude(rel))
            .filter(rel -> !matchesExcludePath(rel))
            .map(rel -> {
                var content = readFile(root, rel);
                return new FileEntry(rel, content, estimateTokens(content));
            })
            .filter(e -> !e.content().isEmpty())
            .filter(e -> !looksBinary(e.content()))
            .filter(e -> e.content().length() <= opts.maxFileSizeBytes())
            .toList();
    }

    private boolean matchesExcludePath(String rel) {
        return opts.excludePathPatterns().stream().anyMatch(glob -> matchesPathPattern(rel, glob));
    }

    /** Pattern: `dir/**` matches anything under dir; `*.ext` matches filename extension. */
    private static boolean matchesPathPattern(String rel, String pattern) {
        if (pattern.endsWith("/**")) {
            var prefix = pattern.substring(0, pattern.length() - 3);
            return rel.equals(prefix) || rel.startsWith(prefix + "/");
        }
        if (pattern.startsWith("*.")) return rel.endsWith(pattern.substring(1));
        return rel.equals(pattern);
    }

    /** Heuristic: NUL byte in first 8 KB → binary. */
    private static boolean looksBinary(String content) {
        int limit = Math.min(content.length(), 8192);
        for (int i = 0; i < limit; i++) if (content.charAt(i) == '\0') return true;
        return false;
    }

    private boolean matchesInclude(String rel) {
        if (opts.includePatterns().isEmpty()) return true;
        String name = Path.of(rel).getFileName().toString();
        return opts.includePatterns().stream().anyMatch(glob -> matchesGlob(name, glob));
    }

    private boolean matchesExclude(String rel) {
        if (opts.excludePatterns().isEmpty()) return false;
        String name = Path.of(rel).getFileName().toString();
        return opts.excludePatterns().stream().anyMatch(glob -> matchesGlob(name, glob));
    }

    /** Minimal glob matcher: supports leading `*.` (extension match) and full filename match. */
    private static boolean matchesGlob(String name, String glob) {
        if (glob.startsWith("*.")) return name.endsWith(glob.substring(1));
        return name.equals(glob);
    }

    private static List<String> gitLsFiles(Path cwd) throws Exception {
        var pb = new ProcessBuilder("git", "ls-files", "--cached", "--others", "--exclude-standard", "-z");
        pb.directory(cwd.toFile());
        pb.redirectErrorStream(true);
        var p = pb.start();
        var bytes = p.getInputStream().readAllBytes();
        int rc = p.waitFor();
        if (rc != 0) {
            throw new IllegalStateException("git ls-files failed: " + new String(bytes));
        }
        String raw = new String(bytes, StandardCharsets.UTF_8);
        return Arrays.stream(raw.split("\0"))
            .filter(s -> !s.isEmpty())
            .toList();
    }

    private static String readFile(Path root, String rel) {
        try {
            return Files.readString(root.resolve(rel));
        } catch (Exception e) {
            return "";
        }
    }

    private static int estimateTokens(String content) {
        return content.length() / 4;
    }
}