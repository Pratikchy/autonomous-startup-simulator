package com.startupsimulator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Autonomous Virtual Startup Simulator backend.
 *
 * <p>The backend owns all authoritative startup state. AI "departments" are
 * modelled as agents that analyse a startup idea, produce contributions,
 * debate, and converge on decisions. As of Phase 2A the CEO reasons with a real
 * LLM (see the {@code agent} package and {@link com.startupsimulator.config.LlmProperties});
 * Development, Marketing and Finance remain deterministic mocks.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAsync
@EnableScheduling
public class StartupSimulatorApplication {

    public static void main(String[] args) {
        com.startupsimulator.config.DotenvLoader.load();
        SpringApplication.run(StartupSimulatorApplication.class, args);
    }
}
