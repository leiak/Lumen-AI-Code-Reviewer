package com.review.council.state;

import com.review.council.config.CouncilConfig;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ReviewStateTest {

    private ReviewState empty() {
        return new ReviewState(
            "s1",
            new CodeDiff("main", "HEAD", List.of()),
            Language.JAVA,
            CouncilConfig.defaults(),
            List.of(),
            List.of(),
            List.of(),
            0,
            Budget.empty(100000, 60000, new BigDecimal("1.00")),
            FlowSignal.CONTINUE
        );
    }

    @Test
    void state_isImmutable_findingsAccumulatedViaCopy() {
        var initial = empty();
        var finding = Finding.of("security", "critical", 42, "msg", "fix");
        var next = initial.withFindingsAdded(List.of(finding));
        assertThat(initial.findings()).isEmpty();
        assertThat(next.findings()).hasSize(1);
    }

    @Test
    void round_incrementsCorrectly() {
        var s = empty();
        assertThat(s.round()).isEqualTo(0);
        assertThat(s.withRound(1).round()).isEqualTo(1);
    }

    @Test
    void budget_tracking() {
        var b = Budget.empty(1000, 1000, new BigDecimal("1.00"));
        var b2 = b.addTokens(500).addCost(new BigDecimal("0.25"));
        assertThat(b2.tokensUsed()).isEqualTo(500);
        assertThat(b2.costUsd()).isEqualByComparingTo("0.25");
        assertThat(b2.tokensExhausted()).isFalse();
    }
}
