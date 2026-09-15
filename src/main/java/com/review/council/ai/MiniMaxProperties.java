package com.review.council.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** MiniMax chat provider. OpenAI-compatible — wired via {@code OpenAiApi} + {@code OpenAiChatModel}. */
@ConfigurationProperties("review.providers.minimax")
public record MiniMaxProperties(String apiKey, String baseUrl, String model) {
    public MiniMaxProperties {
        if (apiKey == null) apiKey = "";
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = "https://api.minimax.chat/v1";
        if (model == null || model.isBlank()) model = "MiniMax-Text-01";
    }
    public boolean isEnabled() { return !apiKey.isBlank(); }
}
