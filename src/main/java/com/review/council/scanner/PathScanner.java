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
        var relPaths = gitLsFiles(root);
        return relPaths.stream()
            .map(rel -> {
                var content = readFile(root, rel);
                return new FileEntry(rel, content, estimateTokens(content));
            })
            .filter(e -> !e.content().isEmpty())
            .toList();
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