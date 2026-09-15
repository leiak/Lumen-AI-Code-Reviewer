package com.review.council.ai;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ChatClientRegistryTest {

    private final ChatClient anthropic = mock(ChatClient.class);
    private final ChatClient openai    = mock(ChatClient.class);
    private final ChatClient deepseek  = mock(ChatClient.class);
    private final ChatClient minimax   = mock(ChatClient.class);

    private ChatClientRegistry registry(Map<String, ChatClient> map) {
        return new ChatClientRegistry(map);
    }

    @Test
    void resolve_gptToOpenAi() {
        var r = registry(Map.of("openai", openai, "anthropic", anthropic));
        assertThat(r.resolve("gpt-5")).isSameAs(openai);
        assertThat(r.resolve("gpt-4o-mini")).isSameAs(openai);
        assertThat(r.resolve("o1-preview")).isSameAs(openai);
    }

    @Test
    void resolve_claudeToAnthropic() {
        var r = registry(Map.of("openai", openai, "anthropic", anthropic));
        assertThat(r.resolve("claude-sonnet-5-20250929")).isSameAs(anthropic);
        assertThat(r.resolve("claude-opus-5")).isSameAs(anthropic);
    }

    @Test
    void resolve_deepseekPrefixRoutesToDeepseek() {
        var r = registry(Map.of("anthropic", anthropic, "openai", openai, "deepseek", deepseek));
        assertThat(r.resolve("deepseek-chat")).isSameAs(deepseek);
        assertThat(r.resolve("deepseek-coder")).isSameAs(deepseek);
        assertThat(r.resolve("deepseek-reasoner")).isSameAs(deepseek);
    }

    @Test
    void resolve_minimaxPrefixRoutesToMinimax() {
        var r = registry(Map.of("anthropic", anthropic, "openai", openai, "minimax", minimax));
        assertThat(r.resolve("MiniMax-Text-01")).isSameAs(minimax);
        assertThat(r.resolve("MiniMax-01")).isSameAs(minimax);
        assertThat(r.resolve("abab6.5s-chat")).isSameAs(minimax);
    }

    @Test
    void resolve_unknownModelDefaultsToAnthropic() {
        var r = registry(Map.of("anthropic", anthropic));
        assertThat(r.resolve("some-new-model")).isSameAs(anthropic);
        assertThat(r.resolve("")).isSameAs(anthropic);
    }

    @Test
    void get_throwsHelpfulErrorWhenProviderMissing() {
        var r = registry(Map.of()); // empty registry
        assertThatThrownBy(() -> r.get("anthropic"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("ANTHROPIC_API_KEY");
        assertThatThrownBy(() -> r.get("openai"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("OPENAI_API_KEY");
        assertThatThrownBy(() -> r.get("deepseek"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("DEEPSEEK_API_KEY");
        assertThatThrownBy(() -> r.get("minimax"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("MINIMAX_API_KEY");
    }

    @Test
    void get_throwsForUnknownProvider() {
        var r = registry(Map.of("anthropic", anthropic));
        assertThatThrownBy(() -> r.get("cohere"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Unknown provider: cohere")
            .hasMessageContaining("anthropic");
    }

    @Test
    void configuredProviders_returnsAllKeys() {
        var r = registry(Map.of("anthropic", anthropic, "deepseek", deepseek, "minimax", minimax));
        assertThat(r.configuredProviders())
            .containsExactlyInAnyOrder("anthropic", "deepseek", "minimax");
    }
}
