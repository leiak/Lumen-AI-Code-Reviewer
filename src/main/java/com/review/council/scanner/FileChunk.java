package com.review.council.scanner;

import java.util.List;

public record FileChunk(String chunkId, List<FileEntry> entries, int totalTokens) {
    public FileChunk {
        entries = List.copyOf(entries);
    }
}