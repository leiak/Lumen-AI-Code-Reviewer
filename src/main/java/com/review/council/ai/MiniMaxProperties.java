package com.review.council.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** MiniMax chat provider. OpenAI-compatible — wired via {@code OpenAiApi} + {@code OpenAiChatModel}. */
@ConfigurationProperties("review.providers.minimax")
public record MiniMaxProperties(String apiKey, String baseUrl, String model) {
    public MiniMaxProperties {
        if (apiKey == null) apiKey = "";
        // Spring AI's OpenAiApi 在 baseUrl 后面拼 /v1/chat/completions，
        // 所以这里只写到 host，**不能带 /v1**，否则会变成 /v1/v1/... → 404
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = "https://api.minimax.chat";
        if (model == null || model.isBlank()) model = "MiniMax-Text-01";
    }
    public boolean isEnabled() { return !apiKey.isBlank(); }
}
