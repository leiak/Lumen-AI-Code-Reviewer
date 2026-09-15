package com.review.council.scanner;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class PathScanner {
    private final ScanOptions opts;

    @Autowired
    public PathScanner(ScanOptions opts) {
        this.opts = opts != null ? opts : ScanOptions.defaults();
    }

    public List<FileEntry> scan() throws Exception {
        // implemented incrementally across Tasks 5-7
        return List.of();
    }
}