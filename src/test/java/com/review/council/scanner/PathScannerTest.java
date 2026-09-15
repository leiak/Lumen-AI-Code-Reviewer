package com.review.council.scanner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.assertThat;

class PathScannerTest {

    @Test
    void scans_java_files_in_git_repo(@TempDir Path repoDir) throws Exception {
        // arrange: init git repo, write two .java files
        runGit(repoDir, "init");
        runGit(repoDir, "config", "user.email", "t@t");
        runGit(repoDir, "config", "user.name", "t");
        Files.writeString(repoDir.resolve("A.java"), "class A {}");
        Files.writeString(repoDir.resolve("B.java"), "class B {}");

        var scanner = new PathScanner(ScanOptions.defaults().withPath(repoDir.toString()));

        // act
        var entries = scanner.scan();

        // assert
        assertThat(entries).hasSize(2);
        assertThat(entries).extracting(FileEntry::path)
            .containsExactlyInAnyOrder("A.java", "B.java");
    }

    private static void runGit(Path dir, String... gitArgs) throws Exception {
        var cmd = new ProcessBuilder("git");
        cmd.command().addAll(java.util.List.of(gitArgs));
        cmd.directory(dir.toFile());
        cmd.redirectErrorStream(true);
        var p = cmd.start();
        int rc = p.waitFor();
        if (rc != 0) throw new AssertionError("git " + String.join(" ", gitArgs) + " failed: " + new String(p.getInputStream().readAllBytes()));
    }

    @Test
    void skips_non_matching_extension(@TempDir Path repoDir) throws Exception {
        runGit(repoDir, "init");
        runGit(repoDir, "config", "user.email", "t@t");
        runGit(repoDir, "config", "user.name", "t");
        Files.writeString(repoDir.resolve("keep.java"), "class K {}");
        Files.writeString(repoDir.resolve("README.md"), "# notes");
        Files.writeString(repoDir.resolve("data.json"), "{}");

        var scanner = new PathScanner(ScanOptions.defaults().withPath(repoDir.toString()));
        var entries = scanner.scan();
        assertThat(entries).extracting(FileEntry::path).containsExactly("keep.java");
    }

    @Test
    void skips_oversized_file(@TempDir Path repoDir) throws Exception {
        runGit(repoDir, "init");
        runGit(repoDir, "config", "user.email", "t@t");
        runGit(repoDir, "config", "user.name", "t");
        Files.writeString(repoDir.resolve("small.java"), "class S {}");
        Files.writeString(repoDir.resolve("big.java"), "x".repeat(600_000));  // > 500KB default

        var opts = ScanOptions.defaults()
            .withPath(repoDir.toString())
            .withMaxFileSizeBytes(500_000);
        var scanner = new PathScanner(opts);
        var entries = scanner.scan();
        assertThat(entries).extracting(FileEntry::path).containsExactly("small.java");
    }
}