package com.startupsimulator.resilience;

import com.startupsimulator.agent.AgentAnalysisException;
import com.startupsimulator.agent.CeoAgent;
import com.startupsimulator.agent.CeoAnalysisException;
import com.startupsimulator.agent.CeoAnalysisOutcome;
import com.startupsimulator.agent.FakeLLMService;
import com.startupsimulator.agent.MockLLMService;
import com.startupsimulator.agent.StartupContext;
import com.startupsimulator.config.AiExecutionMode;
import com.startupsimulator.config.LlmProperties;
import com.startupsimulator.model.Startup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryResilienceTest {

    private List<Long> sleptDurations;
    private Sleeper recordingSleeper;
    private RetryPolicy policy;
    private RetryExecutor executor;

    @BeforeEach
    void setUp() {
        sleptDurations = new ArrayList<>();
        recordingSleeper = sleptDurations::add;
        policy = new RetryPolicy(3, 250L, 2.0, 2000L);
        executor = new RetryExecutor(recordingSleeper);
    }

    @Test
    void testA_successOnFirstAttempt() {
        AtomicInteger attempts = new AtomicInteger(0);

        String result = executor.execute("testA", () -> {
            attempts.incrementAndGet();
            return "SUCCESS";
        }, policy);

        assertThat(result).isEqualTo("SUCCESS");
        assertThat(attempts.get()).isEqualTo(1);
        assertThat(sleptDurations).isEmpty();
    }

    @Test
    void testB_transientFailureThenSuccess() {
        AtomicInteger attempts = new AtomicInteger(0);

        String result = executor.execute("testB", () -> {
            int attempt = attempts.incrementAndGet();
            if (attempt == 1) {
                throw new ResourceAccessException("Network timeout on attempt 1");
            }
            return "RECOVERED";
        }, policy);

        assertThat(result).isEqualTo("RECOVERED");
        assertThat(attempts.get()).isEqualTo(2);
        assertThat(sleptDurations).containsExactly(250L);
    }

    @Test
    void testC_multipleTransientFailures() {
        AtomicInteger attempts = new AtomicInteger(0);

        String result = executor.execute("testC", () -> {
            int attempt = attempts.incrementAndGet();
            if (attempt < 3) {
                throw new ResourceAccessException("Transient error " + attempt);
            }
            return "SUCCESS_ON_ATTEMPT_3";
        }, policy);

        assertThat(result).isEqualTo("SUCCESS_ON_ATTEMPT_3");
        assertThat(attempts.get()).isEqualTo(3);
        assertThat(sleptDurations).containsExactly(250L, 500L);
    }

    @Test
    void testD_retryExhaustion() {
        AtomicInteger attempts = new AtomicInteger(0);

        assertThatThrownBy(() -> executor.execute("testD", () -> {
            attempts.incrementAndGet();
            throw new ResourceAccessException("Persistent network failure");
        }, policy))
                .isInstanceOf(RetryExhaustedException.class)
                .hasMessageContaining("exhausted after 3 attempt(s)");

        assertThat(attempts.get()).isEqualTo(3);
        assertThat(sleptDurations).containsExactly(250L, 500L);
    }

    @Test
    void testE_nonRetryableFailure() {
        AtomicInteger attempts = new AtomicInteger(0);

        assertThatThrownBy(() -> executor.execute("testE", () -> {
            attempts.incrementAndGet();
            throw new AgentAnalysisException("Validation failed for analysis");
        }, policy))
                .isInstanceOf(AgentAnalysisException.class)
                .hasMessageContaining("Validation failed for analysis");

        assertThat(attempts.get()).isEqualTo(1);
        assertThat(sleptDurations).isEmpty();
    }

    @Test
    void testF_backoffIsBounded() {
        RetryPolicy policyWithCap = new RetryPolicy(5, 100L, 3.0, 500L);

        assertThat(policyWithCap.calculateBackoffMs(1)).isEqualTo(100L); // 100 * 3^0
        assertThat(policyWithCap.calculateBackoffMs(2)).isEqualTo(300L); // 100 * 3^1
        assertThat(policyWithCap.calculateBackoffMs(3)).isEqualTo(500L); // min(900, 500) capped
        assertThat(policyWithCap.calculateBackoffMs(4)).isEqualTo(500L); // capped
        assertThat(policyWithCap.calculateBackoffMs(5)).isEqualTo(500L); // capped
    }

    @Test
    void testG_scriptedDemoModePerformsZeroLlmCallsAndNoRetry() {
        MockLLMService mockLlm = new MockLLMService();
        RetryableLLMService retryableLlm = new RetryableLLMService(mockLlm, executor, policy);

        // Scripted / mock provider is not a real provider
        assertThat(retryableLlm.isRealProvider()).isFalse();

        // Complete/generate pass through directly with zero retry delays
        String output = retryableLlm.generate("system", "user prompt");
        assertThat(output).isEqualTo("user prompt");
        assertThat(sleptDurations).isEmpty();
    }

    @Test
    void testH_realModeSemantics_retrySucceedsWithGenuineResult() {
        CeoAnalysisException transientError = new CeoAnalysisException("Simulated transient network failure", new ResourceAccessException("SocketTimeoutException"));
        FakeLLMService fakeLlm = FakeLLMService.returningSequence(
                transientError, // turn 1 fails transiently
                CeoAgentTestUtil.validCeoResponse() // turn 2 succeeds
        );

        RetryableLLMService retryableLlm = new RetryableLLMService(fakeLlm, executor, policy);

        LlmProperties realProps = new LlmProperties();
        realProps.setMode(AiExecutionMode.REAL);
        CeoAgent agent = new CeoAgent(retryableLlm, realProps);

        StartupContext ctx = newContext();
        CeoAnalysisOutcome outcome = agent.runAnalysis(ctx);

        assertThat(outcome.failed()).isFalse();
        assertThat(outcome.provider()).isEqualTo("fake");
        assertThat(ctx.getStartup().isCeoAnalysisFailed()).isFalse();
        assertThat(ctx.getStartup().getExecutiveSummary()).contains("freelancers");
        assertThat(sleptDurations).containsExactly(250L);
    }

    @Test
    void testI_realModeExhaustion_explicitFailureNoDeterministicFallback() {
        CeoAnalysisException transientError = new CeoAnalysisException("Simulated transient network failure", new ResourceAccessException("SocketTimeoutException"));
        FakeLLMService fakeLlm = FakeLLMService.returningSequence(
                transientError,
                transientError,
                transientError
        );
        RetryableLLMService retryableLlm = new RetryableLLMService(fakeLlm, executor, policy);

        LlmProperties realProps = new LlmProperties();
        realProps.setMode(AiExecutionMode.REAL);
        CeoAgent agent = new CeoAgent(retryableLlm, realProps);

        StartupContext ctx = newContext();
        CeoAnalysisOutcome outcome = agent.runAnalysis(ctx);

        // Explicit REAL failure — NO deterministic fallback
        assertThat(outcome.failed()).isTrue();
        assertThat(outcome.provider()).isEqualTo("failed");
        assertThat(ctx.getStartup().isCeoAnalysisFailed()).isTrue();
        assertThat(ctx.getStartup().getExecutiveSummary()).isNull(); // No fabricated summary
        assertThat(sleptDurations).containsExactly(250L, 500L);
    }

    private StartupContext newContext() {
        Startup startup = new Startup();
        startup.setId(100L);
        startup.setName("Test Startup");
        startup.setOriginalIdea("Test Idea");
        return new StartupContext(startup);
    }


    private static class CeoAgentTestUtil {
        static com.startupsimulator.agent.CeoAnalysisResponse validCeoResponse() {
            return new com.startupsimulator.agent.CeoAnalysisResponse(
                    "Become the default budgeting copilot for freelancers.",
                    "Freelancers lack a simple way to forecast irregular income.",
                    "Independent freelancers and solo consultants",
                    "An AI copilot that turns bank data into a live cash-flow forecast.",
                    "Know your runway in seconds, not spreadsheets.",
                    "Freemium SaaS with a Pro subscription.",
                    List.of("Ship a focused MVP", "Protect runway"),
                    List.of("Freelancers feel this pain acutely"),
                    List.of("Underserved niche"),
                    List.of("Adoption risk if value is not obvious", "Scope creep"),
                    "Start with the forecasting engine and bank import; defer social features."
            );
        }
    }
}

