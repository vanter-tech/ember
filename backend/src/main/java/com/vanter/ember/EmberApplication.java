package com.vanter.ember;

import com.vanter.ember.hub.dashboard.HubDashboard;
import java.util.Arrays;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class EmberApplication {

    public static void main(String[] args) {
        if (isHubProfile()) {
            // Spring Boot forces java.awt.headless=true before the environment is even read —
            // the dashboard (and HubTrayIcon) need a real desktop, so this has to win the race
            // by setting the raw JVM property directly, before Spring gets a chance to default it.
            System.setProperty("java.awt.headless", "false");
            HubDashboard.launch(args);
            return;
        }
        SpringApplication.run(EmberApplication.class, args);
    }

    /**
     * Reads the profile straight from the environment, not from Spring — Spring hasn't started
     * yet. This is deliberately the ONLY place that matters: Spring's own DataSource
     * autoconfiguration connects to Postgres during context refresh, which runs before any
     * {@code ApplicationRunner} — too late to start portable Postgres from inside the Spring
     * lifecycle.
     */
    private static boolean isHubProfile() {
        String profiles = System.getenv("SPRING_PROFILES_ACTIVE");
        if (profiles == null) {
            profiles = System.getProperty("spring.profiles.active", "");
        }
        return Arrays.asList(profiles.split(",")).contains("hub");
    }
}
