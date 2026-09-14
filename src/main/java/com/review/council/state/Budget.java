package com.review.council.state;
import java.math.BigDecimal;

public record Budget(
    long tokensUsed,
    long maxTokens,
    long elapsedMs,
    long maxTimeMs,
    BigDecimal costUsd,
    BigDecimal maxCostUsd
) {
    public static Budget empty(long maxTok, long maxMs, BigDecimal maxCost) {
        return new Budget(0, maxTok, 0, maxMs, BigDecimal.ZERO, maxCost);
    }
    public Budget addTokens(long t) { return new Budget(tokensUsed+t, maxTokens, elapsedMs, maxTimeMs, costUsd, maxCostUsd); }
    public Budget addCost(BigDecimal c) { return new Budget(tokensUsed, maxTokens, elapsedMs, maxTimeMs, costUsd.add(c), maxCostUsd); }
    public Budget addElapsed(long ms) { return new Budget(tokensUsed, maxTokens, elapsedMs+ms, maxTimeMs, costUsd, maxCostUsd); }

    public boolean tokensExhausted() { return tokensUsed >= maxTokens; }
    public boolean timeExhausted() { return elapsedMs >= maxTimeMs; }
    public boolean costExhausted() { return costUsd.compareTo(maxCostUsd) >= 0; }
}
