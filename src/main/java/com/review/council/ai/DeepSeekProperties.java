package com.review.council.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** DeepSeek chat provider. OpenAI-compatible — wired via {@code OpenAiApi} + {@code OpenAiChatModel}. */
@ConfigurationProperties("review.providers.deepseek")
public record DeepSeekProperties(String apiKey, String baseUrl, String model) {
    public DeepSeekProperties {
        if (apiKey == null) apiKey = "";
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = "https://api.deepseek.com/v1";
        if (model == null || model.isBlank()) model = "deepseek-chat";
    }
    public boolean isEnabled() { return !apiKey.isBlank(); }
}
