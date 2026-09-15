package com.review.council.scanner;

import com.review.council.audit.AuditRepository;
import com.review.council.audit.LlmCallRepository;
import com.review.council.config.CouncilConfig;
import com.review.council.config.ReviewerConfig;
import com.review.council.nodes.NodeContext;
import com.review.council.nodes.NodeInputs;
import com.review.council.reviewer.GenericReviewerNode;
import com.review.council.state.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.stream.Collectors;

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
        return run(sessionId, config, chunks, defaultConcurrency);
    }

    public ScanResult run(String sessionId, CouncilConfig config, List<FileChunk> chunks,
                          int concurrency) {
        long start = System.currentTimeMillis();
        var ctx = new NodeContext(sessionId, audit, llmCalls, null,
            Budget.empty(config.budget().maxTokens(),
                config.budget().maxTimeSeconds() * 1000,
                config.budget().maxCostUsd()),
            Map.of());

        var pool = java.util.concurrent.Executors.newFixedThreadPool(concurrency);
        var futures = new java.util.ArrayList<
            java.util.concurrent.CompletableFuture<List<Finding>>>();

        try {
            for (var chunk : chunks) {
                for (var rc : config.reviewers()) {
                    if (!rc.enabled()) continue;
                    futures.add(java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                        try {
                            var diff = new CodeDiff(chunk.chunkId(), "HEAD",
                                chunk.entries().stream()
                                    .map(e -> new CodeDiff.FileDiff(e.path(), "", e.content(), List.of()))
                                    .toList());
                            var state = new ReviewState(sessionId, diff, Language.JAVA, config,
                                List.of(), List.of(), List.of(), 0, ctx.budget(), FlowSignal.CONTINUE);
                            var findings = reviewer.apply(
                                new NodeInputs.ReviewerInput(state, rc), ctx);
                            return findings.stream()
                                .map(f -> new Finding(f.id(), f.reviewerRole(), f.severity(),
                                    f.line(), f.message(), f.suggestedFix(),
                                    chunk.chunkId(), f.filePath()))
                                .toList();
                        } catch (Exception e) {
                            audit.record(sessionId, "scanner", "reviewer_error",
                                "{\"chunk\":\"" + chunk.chunkId() + "\",\"reviewer\":\""
                                    + rc.role() + "\",\"error\":\"" + e.getMessage() + "\"}");
                            return List.<Finding>of();
                        }
                    }, pool));
                }
            }

            List<Finding> all = futures.stream()
                .flatMap(f -> f.join().stream())
                .toList();

            long durationMs = System.currentTimeMillis() - start;
            double cost = llmCalls.totalCostFor(sessionId);
            var reviewers = config.reviewers().stream().map(ReviewerConfig::role).toList();
            return new ScanResult(sessionId,
                chunks.stream().mapToInt(c -> c.entries().size()).sum(),
                chunks.size(), reviewers, all, cost, durationMs);
        } finally {
            pool.shutdown();
        }
    }
}