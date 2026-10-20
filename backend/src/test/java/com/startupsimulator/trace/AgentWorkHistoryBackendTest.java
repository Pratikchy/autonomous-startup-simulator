package com.startupsimulator.trace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.startupsimulator.dto.response.ExecutionTraceEntryDto;
import com.startupsimulator.model.enums.AgentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class AgentWorkHistoryBackendTest {

    @Autowired
    private ExecutionTraceRepository traceRepository;

    private ExecutionTraceService traceService;

    @BeforeEach
    void setUp() {
        traceService = new ExecutionTraceService(traceRepository, new ObjectMapper());
    }

    @Test
    @DisplayName("Filter trace by agent returns activities for that agent or participant")
    void testFilterTraceByAgent() {
        Long startupId = 701L;

        // CEO reasoning
        traceService.recordLLMReasoning(startupId, "run-701", AgentType.CEO,
                "STRATEGIC_ANALYSIS", "CEO sets initial market positioning", Map.of("vision", "AI platform"));

        // Development tool use
        traceService.recordToolCall(startupId, "run-701", AgentType.DEVELOPMENT,
                "development_effort_estimator", Map.of("stack", "React, Node"));

        // Marketing reasoning
        traceService.recordLLMReasoning(startupId, "run-701", AgentType.MARKETING,
                "GTM_PLAN", "Marketing identifies organic growth channels", Map.of());

        // Finance tool use
        traceService.recordToolCall(startupId, "run-701", AgentType.FINANCE,
                "financial_calculator", Map.of("burnRate", 5000));

        // Handoff: CEO asks Finance to review runway
        traceService.recordHandoff(startupId, "run-701", AgentType.CEO, AgentType.FINANCE,
                "Verify budget constraints", "{\"action\":\"verify\"}");

        // 1. Query for CEO
        List<ExecutionTraceEntryDto> ceoTrace = traceService.getTraceByAgent(startupId, "CEO");
        assertThat(ceoTrace).hasSize(2);
        assertThat(ceoTrace).allMatch(e -> "CEO".equalsIgnoreCase(e.agent()) || "CEO".equalsIgnoreCase(e.sender()));

        // 2. Query for DEVELOPMENT
        List<ExecutionTraceEntryDto> devTrace = traceService.getTraceByAgent(startupId, "DEVELOPMENT");
        assertThat(devTrace).hasSize(1);
        assertThat(devTrace.get(0).toolName()).isEqualTo("development_effort_estimator");

        // 3. Query for MARKETING
        List<ExecutionTraceEntryDto> mktTrace = traceService.getTraceByAgent(startupId, "MARKETING");
        assertThat(mktTrace).hasSize(1);
        assertThat(mktTrace.get(0).agent()).isEqualTo("MARKETING");

        // 4. Query for FINANCE (should include Finance tool call + CEO->Finance handoff)
        List<ExecutionTraceEntryDto> finTrace = traceService.getTraceByAgent(startupId, "FINANCE");
        assertThat(finTrace).hasSize(2);

        // 5. Query for ALL or null returns all 5
        List<ExecutionTraceEntryDto> allTrace = traceService.getTraceByAgent(startupId, "ALL");
        assertThat(allTrace).hasSize(5);

        List<ExecutionTraceEntryDto> defaultTrace = traceService.getTraceByAgent(startupId, null);
        assertThat(defaultTrace).hasSize(5);
    }

    @Test
    @DisplayName("Agent work history is strictly isolated between startups")
    void testStartupIsolation() {
        Long startupA = 801L;
        Long startupB = 802L;

        traceService.recordLLMReasoning(startupA, "run-A", AgentType.CEO, "ANALYSIS", "Startup A CEO", Map.of());
        traceService.recordLLMReasoning(startupB, "run-B", AgentType.CEO, "ANALYSIS", "Startup B CEO", Map.of());

        List<ExecutionTraceEntryDto> traceA = traceService.getTraceByAgent(startupA, "CEO");
        List<ExecutionTraceEntryDto> traceB = traceService.getTraceByAgent(startupB, "CEO");

        assertThat(traceA).hasSize(1);
        assertThat(traceA.get(0).startupId()).isEqualTo(startupA);
        assertThat(traceA.get(0).reason()).contains("Startup A");

        assertThat(traceB).hasSize(1);
        assertThat(traceB.get(0).startupId()).isEqualTo(startupB);
        assertThat(traceB.get(0).reason()).contains("Startup B");
    }
}
