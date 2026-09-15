package com.review.council.scanner;

import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class FileChunker {
    private final int maxTokensPerChunk;

    public FileChunker() { this(50_000); }
    public FileChunker(int maxTokensPerChunk) {
        this.maxTokensPerChunk = maxTokensPerChunk;
    }

    public List<FileChunk> chunk(List<FileEntry> entries) {
        // First-fit-decreasing by tokens desc - minimizes total chunks
        var sorted = entries.stream()
            .sorted(Comparator.comparingInt(FileEntry::tokens).reversed())
            .toList();

        List<FileChunk> chunks = new ArrayList<>();
        List<FileEntry> current = new ArrayList<>();
        int currentTokens = 0;
        int nextChunkNum = 1;

        for (var e : sorted) {
            if (e.tokens() > maxTokensPerChunk) {
                if (!current.isEmpty()) {
                    chunks.add(makeChunk(nextChunkNum++, current, currentTokens));
                    current = new ArrayList<>();
                    currentTokens = 0;
                }
                // Single oversized file -> its own chunk, even if over limit
                chunks.add(makeChunk(nextChunkNum++, List.of(e), e.tokens()));
                continue;
            }
            if (currentTokens + e.tokens() > maxTokensPerChunk) {
                chunks.add(makeChunk(nextChunkNum++, current, currentTokens));
                current = new ArrayList<>();
                currentTokens = 0;
            }
            current.add(e);
            currentTokens += e.tokens();
        }
        if (!current.isEmpty()) {
            chunks.add(makeChunk(nextChunkNum++, current, currentTokens));
        }
        return chunks;
    }

    private static FileChunk makeChunk(int n, List<FileEntry> entries, int tokens) {
        return new FileChunk(String.format("chunk-%03d", n), entries, tokens);
    }
}