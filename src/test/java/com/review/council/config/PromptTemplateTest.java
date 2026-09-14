package com.review.council.config;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptTemplateTest {

    @Test
    void renders_placeholders() {
        var t = new PromptTemplate("Hello {{name}}, round {{round}}");
        assertThat(t.render(Map.of("name", "Claude", "round", "1")))
            .isEqualTo("Hello Claude, round 1");
    }

    @Test
    void missingPlaceholder_throws() {
        var t = new PromptTemplate("Hi {{name}}");
        assertThatThrownBy(() -> t.render(Map.of()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void loads_builtInArchitectPrompt() {
        var t = PromptTemplate.fromResource("prompts/architect.md");
        assertThat(t.raw()).contains("{{language}}");
        assertThat(t.raw()).contains("{{diff}}");
    }
}
