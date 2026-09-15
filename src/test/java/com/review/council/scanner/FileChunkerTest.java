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

    private static FileEntry entry(String path, String content) {
        return new FileEntry(path, content, content.length() / 4);
    }
}