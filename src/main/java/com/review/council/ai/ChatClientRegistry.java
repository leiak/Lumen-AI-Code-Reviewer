package com.review.council.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.Set;

@Component
public class ChatClientRegistry {
    private final Map<String, ChatClient> clients;

    public ChatClientRegistry(Map<String, ChatClient> clients) {
        this.clients = clients;
    }

    public ChatClient get(String providerName) {
        var c = clients.get(providerName);
        if (c != null) return c;
        throw new IllegalStateException(switch (providerName) {
            case "anthropic" -> "Anthropic ChatClient not configured. Set ANTHROPIC_API_KEY.";
            case "openai"    -> "OpenAI ChatClient not configured. Set OPENAI_API_KEY.";
            case "deepseek"  -> "DeepSeek ChatClient not configured. Set DEEPSEEK_API_KEY.";
            case "minimax"   -> "MiniMax ChatClient not configured. Set MINIMAX_API_KEY.";
            default -> "Unknown provider: " + providerName
                + ". Configured: " + clients.keySet();
        });
    }

    /** Resolves a model name to its provider's ChatClient.
     *  Routing:
     *    gpt-*, o*                   → openai
     *    deepseek-*, deepseek-chat   → deepseek
     *    MiniMax-*, abab*            → minimax
     *    claude-*                    → anthropic
     *    default                     → anthropic
     */
    public ChatClient resolve(String modelName) {
        if (modelName == null || modelName.isBlank()) return get("anthropic");
        var lower = modelName.toLowerCase();
        if (lower.startsWith("gpt-") || lower.startsWith("o")) return get("openai");
        if (lower.startsWith("deepseek")) return get("deepseek");
        if (modelName.startsWith("MiniMax") || lower.startsWith("abab")) return get("minimax");
        if (lower.startsWith("claude-")) return get("anthropic");
        return get("anthropic"); // sensible default
    }

    public Set<String> configuredProviders() {
        return clients.keySet();
    }
}
