package com.review.council.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import java.util.Optional;

@Component
public class ChatClientRegistry {
    private final ObjectProvider<ChatClient> provider;

    public ChatClientRegistry(ObjectProvider<ChatClient> provider) {
        this.provider = provider;
    }

    public ChatClient get(String providerName) {
        return switch (providerName) {
            case "anthropic" -> findByName("anthropic").orElseThrow(() ->
                new IllegalStateException("Anthropic ChatClient not configured. Set ANTHROPIC_API_KEY."));
            case "openai" -> findByName("openai").orElseThrow(() ->
                new IllegalStateException("OpenAI ChatClient not configured. Set OPENAI_API_KEY."));
            default -> throw new IllegalArgumentException("Unknown provider: " + providerName);
        };
    }

    /** Resolves "claude-*" → anthropic; "gpt-*" / "o*" → openai; defaults anthropic. */
    public ChatClient resolve(String modelName) {
        if (modelName.startsWith("gpt-") || modelName.startsWith("o")) return get("openai");
        return get("anthropic");
    }

    private Optional<ChatClient> findByName(String name) {
        return provider.stream()
            .filter(c -> c.getClass().getSimpleName().toLowerCase().contains(name))
            .findFirst();
    }
}
