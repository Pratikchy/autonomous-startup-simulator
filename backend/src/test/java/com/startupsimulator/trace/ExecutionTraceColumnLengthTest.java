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
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Regression tests for the DECISION column length bug: a long CEO reasoning
 * summary (~297 chars) overflowed the former {@code VARCHAR(100)} decision
 * column and crashed the simulation with DataIntegrityViolationException.
 *
 * <p>Uses {@code @DataJpaTest} so the entries are persisted to the real H2
 * schema — if the column were still bounded at 100, these saves would throw.
 */
@DataJpaTest
class ExecutionTraceColumnLengthTest {

    @Autowired
    private ExecutionTraceRepository traceRepository;

    private ExecutionTraceService traceService;

    private static final String LONG_TEXT =
            "The CEO recommends targeting independent freelancers and solo consultants with an AI copilot "
            + "that turns raw bank-transaction data into a live, continuously-updated cash-flow forecast, so "
            + "the user can see their runway in seconds instead of maintaining fragile spreadsheets, while "
            + "deferring all social and community features to a later release to protect the initial runway.";

    @BeforeEach
    void setUp() {
        traceService = new ExecutionTraceService(traceRepository, new ObjectMapper());
        assertThat(LONG_TEXT.length()).isGreaterThan(100); // guards the regression premise
    }

    @Test
    @DisplayName("A decision longer than 100 chars persists in full, without truncation")
    void longDecisionPersistsWithoutLoss() {
        Long startupId = 701L;

        assertThatCode(() -> traceService.recordEntry(
                startupId, "run-701", ExecutionTraceType.ORCHESTRATION_DECISION,
                "ORCHESTRATOR", null, null,
                "DECIDE", LONG_TEXT, "orchestration rationale",
                null, null, null, "ACCEPTED", Map.of()))
                .doesNotThrowAnyException();

        List<ExecutionTraceEntryDto> trace = traceService.getTrace(startupId);
        assertThat(trace).hasSize(1);
        ExecutionTraceEntryDto entry = trace.get(0);
        assertThat(entry.decision()).isEqualTo(LONG_TEXT);               // full value preserved
        assertThat(entry.decision().length()).isEqualTo(LONG_TEXT.length());
    }

    @Test
    @DisplayName("Long LLM reasoning is stored (in the TEXT reason column) and does not crash")
    void longLlmReasoningDoesNotCrash() {
        Long startupId = 702L;

        assertThatCode(() -> traceService.recordLLMReasoning(
                startupId, "run-702", AgentType.CEO, "STRATEGIC_ANALYSIS", LONG_TEXT,
                Map.of("provider", "openai")))
                .doesNotThrowAnyException();

        List<ExecutionTraceEntryDto> trace = traceService.getTrace(startupId);
        assertThat(trace).hasSize(1);
        ExecutionTraceEntryDto entry = trace.get(0);
        assertThat(entry.type()).isEqualTo(ExecutionTraceType.LLM_REASONING);
        assertThat(entry.action()).isEqualTo("STRATEGIC_ANALYSIS");
        assertThat(entry.reason()).isEqualTo(LONG_TEXT);                 // full reasoning, no truncation
    }
}
