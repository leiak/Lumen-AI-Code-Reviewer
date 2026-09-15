package com.review.council.scanner;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class FileChunkerTest {

    @Test
    void packs_small_files_into_one_chunk() {
        var entries = List.of(
            entry("A.java", "x".repeat(40)),
            entry("B.java", "y".repeat(40)),
            entry("C.java", "z".repeat(40))
        );
        var chunks = new FileChunker(50_000).chunk(entries);
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).entries()).hasSize(3);
        assertThat(chunks.get(0).totalTokens()).isEqualTo(30);
    }

    @Test
    void greedy_bin_pack_splits_when_full() {
        // 4 files of 200 tokens, max=300 -> first-fit-decreasing; all equal so order preserved:
        // each file (200) + current (200) = 400 > 300, so each becomes its own chunk -> 4 chunks
        var entries = List.of(
            entry("A.java", "x".repeat(800)),
            entry("B.java", "y".repeat(800)),
            entry("C.java", "z".repeat(800)),
            entry("D.java", "w".repeat(800))
        );
        var chunks = new FileChunker(300).chunk(entries);
        assertThat(chunks).hasSize(4);
        assertThat(chunks.get(0).totalTokens()).isEqualTo(200);
    }

    @Test
    void single_oversized_file_gets_own_chunk() {
        var entries = List.of(
            entry("huge.java", "z".repeat(1_000_000)),  // ~250k tokens, way over 50k limit
            entry("small.java", "x".repeat(40))
        );
        var chunks = new FileChunker(50_000).chunk(entries);
        assertThat(chunks).hasSize(2);
        // Sorted desc: huge first, gets own chunk; small goes alone in second chunk
        assertThat(chunks.get(0).entries()).extracting(FileEntry::path).containsExactly("huge.java");
        assertThat(chunks.get(1).entries()).extracting(FileEntry::path).containsExactly("small.java");
    }

    private static FileEntry entry(String path, String content) {
        return new FileEntry(path, content, content.length() / 4);
    }
}