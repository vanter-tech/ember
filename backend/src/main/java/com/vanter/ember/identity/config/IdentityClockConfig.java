package com.vanter.ember.identity.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IdentityClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
