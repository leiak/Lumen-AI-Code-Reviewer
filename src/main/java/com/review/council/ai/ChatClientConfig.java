package com.review.council.ai;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {
    @Bean
    @ConditionalOnBean(AnthropicChatModel.class)
    public ChatClient anthropic(AnthropicChatModel model) {
        return ChatClient.create(model);
    }

    @Bean
    @ConditionalOnBean(OpenAiChatModel.class)
    public ChatClient openai(OpenAiChatModel model) {
        return ChatClient.create(model);
    }
}
