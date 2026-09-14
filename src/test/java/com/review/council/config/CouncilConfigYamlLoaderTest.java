package com.review.council.config;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;

class CouncilConfigYamlLoaderTest {

    private final CouncilConfigYamlLoader loader = new CouncilConfigYamlLoader();

    @Test
    void loads_minimalYaml() throws Exception {
        var yaml = """
            council:
              name: team-test
            loop:
              max-rounds: 5
            reviewers:
              - role: security
                model: gpt-5
                prompt-file: prompts/security.md
            """;
        var cfg = loader.load(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
        assertThat(cfg.name()).isEqualTo("team-test");
        assertThat(cfg.maxRounds()).isEqualTo(5);
        assertThat(cfg.reviewers()).hasSize(1);
        assertThat(cfg.reviewers().get(0).role()).isEqualTo("security");
        assertThat(cfg.reviewers().get(0).model()).isEqualTo("gpt-5");
    }

    @Test
    void loads_humanGatesAndBudget() throws Exception {
        var yaml = """
            council:
              name: prod
            human-gates:
              default: never
              per-reviewer:
                security:
                  - severity: critical
                    action: must_approve
              path-rules:
                - pattern: "**/auth/**"
                  gate: always
            budget:
              max-tokens: 100000
              max-cost-usd: "2.50"
            """;
        var cfg = loader.load(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
        assertThat(cfg.humanGates().perReviewer()).containsKey("security");
        assertThat(cfg.humanGates().pathRules()).hasSize(1);
        assertThat(cfg.budget().maxTokens()).isEqualTo(100000);
        assertThat(cfg.budget().maxCostUsd()).isEqualByComparingTo("2.50");
    }
}
