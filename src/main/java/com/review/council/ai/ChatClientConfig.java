package com.review.council.ai;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({DeepSeekProperties.class, MiniMaxProperties.class})
public class ChatClientConfig {
    @Bean("anthropic")
    @ConditionalOnBean(AnthropicChatModel.class)
    public ChatClient anthropic(AnthropicChatModel model) {
        return ChatClient.create(model);
    }

    @Bean("openai")
    @ConditionalOnBean(OpenAiChatModel.class)
    public ChatClient openai(OpenAiChatModel model) {
        return ChatClient.create(model);
    }

    @Bean("deepseek")
    @org.springframework.boot.autoconfigure.condition.ConditionalOnExpression(
        "'${review.providers.deepseek.api-key:}'.length() > 0")
    public ChatClient deepseek(DeepSeekProperties props) {
        return openAiCompatible("deepseek", props.baseUrl(), props.apiKey(), props.model());
    }

    @Bean("minimax")
    @org.springframework.boot.autoconfigure.condition.ConditionalOnExpression(
        "'${review.providers.minimax.api-key:}'.length() > 0")
    public ChatClient minimax(MiniMaxProperties props) {
        return openAiCompatible("minimax", props.baseUrl(), props.apiKey(), props.model());
    }

    /** Shared builder for OpenAI-compatible APIs (DeepSeek, MiniMax, etc.). */
    private static ChatClient openAiCompatible(String name, String baseUrl, String apiKey, String model) {
        var api = OpenAiApi.builder()
            .baseUrl(baseUrl)
            .apiKey(apiKey)
            .build();
        var options = OpenAiChatOptions.builder().model(model).build();
        var chatModel = OpenAiChatModel.builder()
            .openAiApi(api)
            .defaultOptions(options)
            .build();
        return ChatClient.create(chatModel);
    }
}
