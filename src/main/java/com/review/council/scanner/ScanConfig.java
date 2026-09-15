package com.review.council.scanner;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ScanConfig {
    @Bean
    public ScanOptions scanOptions() {
        return ScanOptions.defaults();
    }
}