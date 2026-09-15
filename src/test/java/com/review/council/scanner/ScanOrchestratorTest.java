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
}