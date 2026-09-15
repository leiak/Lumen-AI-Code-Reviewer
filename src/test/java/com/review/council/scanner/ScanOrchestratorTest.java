package com.review.council.scanner;

import com.review.council.audit.AuditRepository;
import com.review.council.audit.LlmCallRepository;
import com.review.council.config.*;
import com.review.council.reviewer.GenericReviewerNode;
import com.review.council.state.*;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScanOrchestratorTest {

    @Test
    void fans_out_to_each_reviewer_for_each_chunk() throws Exception {
        // 2 chunks, 2 reviewers -> 4 LLM calls total
        var chunks = List.of(
            new FileChunk("chunk-001", List.of(new FileEntry("A.java", "x", 1)), 1),
            new FileChunk("chunk-002", List.of(new FileEntry("B.java", "y", 1)), 1)
        );

        var reviewerNode = Mockito.mock(GenericReviewerNode.class);
        when(reviewerNode.apply(any(), any())).thenAnswer(inv -> {
            var input = (com.review.council.nodes.NodeInputs.ReviewerInput) inv.getArgument(0);
            return List.of(Finding.of(input.config().role(), "minor", 1, "issue", "fix", null, null));
        });

        var config = new CouncilConfig("test", "1", 1, "no-critical-and-no-major",
            List.of(ReviewerConfig.defaults("architect"), ReviewerConfig.defaults("security")),
            AggregatorConfig.defaults(), HumanGateConfig.defaults(),
            FixerConfig.defaults(), ReReviewerConfig.defaults(),
            new BudgetConfig(100_000, 60, new java.math.BigDecimal("1.00"), "abort"),
            OutputConfig.defaults());

        var orch = new ScanOrchestrator(reviewerNode, Mockito.mock(AuditRepository.class),
            Mockito.mock(LlmCallRepository.class), 3);

        var result = orch.run("rev-test", config, chunks);

        assertThat(result.totalChunks()).isEqualTo(2);
        // 2 chunks x 2 reviewers = 4 LLM calls -> 4 findings
        assertThat(result.findings()).hasSize(4);
        Mockito.verify(reviewerNode, Mockito.times(4)).apply(any(), any());
    }

    @Test
    void uses_executor_with_configured_concurrency() throws Exception {
        var chunks = List.of(
            new FileChunk("chunk-001", List.of(new FileEntry("A.java", "x", 1)), 1),
            new FileChunk("chunk-002", List.of(new FileEntry("B.java", "y", 1)), 1),
            new FileChunk("chunk-003", List.of(new FileEntry("C.java", "z", 1)), 1)
        );

        var reviewerNode = Mockito.mock(GenericReviewerNode.class);
        when(reviewerNode.apply(any(), any())).thenReturn(List.of());

        var config = new CouncilConfig("test", "1", 1, "no-critical-and-no-major",
            List.of(ReviewerConfig.defaults("architect")),
            AggregatorConfig.defaults(), HumanGateConfig.defaults(),
            FixerConfig.defaults(), ReReviewerConfig.defaults(),
            new BudgetConfig(100_000, 60, new java.math.BigDecimal("1.00"), "abort"),
            OutputConfig.defaults());

        var orch = new ScanOrchestrator(reviewerNode, Mockito.mock(AuditRepository.class),
            Mockito.mock(LlmCallRepository.class), 3);
        var result = orch.run("rev-test2", config, chunks);

        assertThat(result.totalChunks()).isEqualTo(3);
        // 3 chunks x 1 reviewer = 3 calls
        verify(reviewerNode, times(3)).apply(any(), any());
    }

    @Test
    void one_reviewer_failure_does_not_abort_others() throws Exception {
        var chunks = List.of(
            new FileChunk("chunk-001", List.of(new FileEntry("A.java", "x", 1)), 1)
        );

        var reviewerNode = Mockito.mock(GenericReviewerNode.class);
        // First call throws, second returns a finding
        when(reviewerNode.apply(any(), any()))
            .thenThrow(new RuntimeException("LLM down"))
            .thenReturn(List.of(Finding.of("security", "major", 5, "xss", "escape")));

        var config = new CouncilConfig("test", "1", 1, "no-critical-and-no-major",
            List.of(ReviewerConfig.defaults("architect"), ReviewerConfig.defaults("security")),
            AggregatorConfig.defaults(), HumanGateConfig.defaults(),
            FixerConfig.defaults(), ReReviewerConfig.defaults(),
            new BudgetConfig(100_000, 60, new java.math.BigDecimal("1.00"), "abort"),
            OutputConfig.defaults());

        var audit = Mockito.mock(AuditRepository.class);
        var orch = new ScanOrchestrator(reviewerNode, audit,
            Mockito.mock(LlmCallRepository.class), 2);
        var result = orch.run("rev-fail", config, chunks);

        // 2 reviewers attempted, 1 succeeded with 1 finding
        assertThat(result.findings()).hasSize(1);
        // Audit recorded the error
        verify(audit).record(Mockito.eq("rev-fail"), Mockito.eq("scanner"),
            Mockito.eq("reviewer_error"), Mockito.anyString());
    }
}