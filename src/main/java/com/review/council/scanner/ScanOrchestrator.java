package com.review.council.scanner;

import com.review.council.audit.AuditRepository;
import com.review.council.audit.LlmCallRepository;
import com.review.council.config.CouncilConfig;
import com.review.council.reviewer.GenericReviewerNode;
import com.review.council.state.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class ScanOrchestrator {
    private final GenericReviewerNode reviewer;
    private final AuditRepository audit;
    private final LlmCallRepository llmCalls;
    private final int defaultConcurrency;

    public ScanOrchestrator(GenericReviewerNode reviewer,
                            AuditRepository audit,
                            LlmCallRepository llmCalls,
                            @Value("${review.scan.concurrency:3}") int defaultConcurrency) {
        this.reviewer = reviewer;
        this.audit = audit;
        this.llmCalls = llmCalls;
        this.defaultConcurrency = defaultConcurrency;
    }

    public ScanResult run(String sessionId, CouncilConfig config, List<FileChunk> chunks) {
        // implemented incrementally in Tasks 12-15
        return new ScanResult(sessionId, 0, chunks.size(), List.of(), List.of(), 0.0, 0L);
    }
}