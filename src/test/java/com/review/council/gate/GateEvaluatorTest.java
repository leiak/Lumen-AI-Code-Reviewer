package com.review.council.gate;

import com.review.council.config.HumanGateConfig;
import com.review.council.state.Finding;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class GateEvaluatorTest {

    @Test
    void critical_finding_triggersGate() {
        var config = new HumanGateConfig("never",
            Map.of("security", List.of(new HumanGateConfig.Rule("critical", "must_approve"))),
            List.of());
        var eval = new GateEvaluator(config);
        var gates = eval.evaluate(
            List.of(Finding.of("security", "critical", 1, "x", "")),
            "src/Foo.java");
        assertThat(gates).hasSize(1);
        assertThat(gates.get(0).action()).isEqualTo("must_approve");
    }

    @Test
    void pathRule_auth_overrides() {
        var config = new HumanGateConfig("never", Map.of(),
            List.of(new HumanGateConfig.PathRule("**/auth/**", "always")));
        var eval = new GateEvaluator(config);
        var gates = eval.evaluate(
            List.of(Finding.of("x", "minor", 1, "m", "")),
            "src/auth/Login.java");
        assertThat(gates).hasSize(1);
    }

    @Test
    void noMatchingRule_noGate() {
        var eval = new GateEvaluator(HumanGateConfig.defaults());
        var gates = eval.evaluate(
            List.of(Finding.of("x", "minor", 1, "m", "")),
            "src/foo/Bar.java");
        assertThat(gates).isEmpty();
    }
}
