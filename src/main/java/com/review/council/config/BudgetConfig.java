package com.review.council.config;
import java.math.BigDecimal;
public record BudgetConfig(
    long maxTokens,
    long maxTimeSeconds,
    BigDecimal maxCostUsd,
    String onExceeded
) {
    public static BudgetConfig defaults() {
        return new BudgetConfig(500000, 1800, new BigDecimal("5.00"), "pause_and_ask");
    }
}
