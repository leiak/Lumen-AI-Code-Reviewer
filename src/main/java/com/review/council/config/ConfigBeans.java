package com.review.council.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Default beans for sub-configs; runtime values come from CouncilConfig. */
@Configuration
public class ConfigBeans {
    @Bean public AggregatorConfig aggregatorConfig() { return AggregatorConfig.defaults(); }
    @Bean public HumanGateConfig humanGateConfig() { return HumanGateConfig.defaults(); }
    @Bean public FixerConfig fixerConfig() { return FixerConfig.defaults(); }
    @Bean public ReReviewerConfig reReviewerConfig() { return ReReviewerConfig.defaults(); }
    @Bean public BudgetConfig budgetConfig() { return BudgetConfig.defaults(); }
    @Bean public OutputConfig outputConfig() { return OutputConfig.defaults(); }
}
