package com.startupsimulator.trace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.startupsimulator.agent.AgentAnalysisOutcome;
import com.startupsimulator.agent.CeoAgent;
import com.startupsimulator.agent.CeoAnalysisOutcome;
import com.startupsimulator.agent.DeveloperAgent;
import com.startupsimulator.agent.FinanceAgent;
import com.startupsimulator.agent.LLMService;
import com.startupsimulator.agent.MarketingAgent;
import com.startupsimulator.agent.StartupContext;
import com.startupsimulator.agent.recovery.AgentRecoveryEngine;
import com.startupsimulator.agent.recovery.AgentRecoveryOutcome;
import com.startupsimulator.agent.recovery.AgentRecoveryRequest;
import com.startupsimulator.config.AiExecutionMode;
import com.startupsimulator.config.LlmProperties;
import com.startupsimulator.dto.response.ExecutionTraceEntryDto;
import com.startupsimulator.model.ExecutionTask;
import com.startupsimulator.model.Startup;
import com.startupsimulator.model.enums.AgentType;
import com.startupsimulator.model.enums.TaskPriority;
import com.startupsimulator.model.enums.TaskStatus;
import com.startupsimulator.orchestrator.OrchestrationActionExecutor;
import com.startupsimulator.repository.AgentMessageRepository;
import com.startupsimulator.repository.ExecutionTaskRepository;
import com.startupsimulator.service.AgentInbox;
import com.startupsimulator.service.EventService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DataJpaTest
class ExecutionTraceCompletenessTest {

    @Autowired
    private ExecutionTraceRepository traceRepository;

    @Autowired
    private ExecutionTaskRepository taskRepository;

    @SuppressWarnings("unused")
    @Autowired
    private AgentMessageRepository messageRepository;

    private ExecutionTraceService traceService;
    private EventService eventService;

    @BeforeEach
    void setUp() {
        traceService = new ExecutionTraceService(traceRepository, new ObjectMapper());
        eventService = mock(EventService.class);
    }

    @Test
    @DisplayName("A. LLM reasoning creates an LLM_REASONING trace entry")
    void testLlmReasoningCreatesTraceEntry() {
        Long startupId = 101L;

        traceService.recordLLMReasoning(startupId, "run-101", AgentType.CEO,
                "STRATEGIC_ANALYSIS", "Executive strategic vision for AI platform",
                Map.of("provider", "openai", "summary", "Executive strategic vision"));

        List<ExecutionTraceEntryDto> trace = traceService.getTrace(startupId);
        assertThat(trace).hasSize(1);
        ExecutionTraceEntryDto entry = trace.get(0);
        assertThat(entry.type()).isEqualTo(ExecutionTraceType.LLM_REASONING);
        assertThat(entry.agent()).isEqualTo("CEO");
        assertThat(entry.action()).isEqualTo("STRATEGIC_ANALYSIS");
        assertThat(entry.reason()).isEqualTo("Executive strategic vision for AI platform");
    }

    @Test
    @DisplayName("B. Task status transition creates TASK_STATE_CHANGE")
    void testTaskStatusTransitionCreatesStateChange() {
        Long startupId = 102L;

        ExecutionTask task = new ExecutionTask(startupId, AgentType.DEVELOPMENT, "Dev Lead",
                "Build API Gateway", "Core route routing", TaskPriority.HIGH, 5);
        task.setStatus(TaskStatus.PENDING);
        task = taskRepository.save(task);

        traceService.recordTaskStateChange(startupId, "run-102", AgentType.DEVELOPMENT.name(),
                task.getId(), task.getTitle(), null, TaskStatus.PENDING, "Task created");

        TaskStatus oldStatus = task.getStatus();
        task.setStatus(TaskStatus.IN_PROGRESS);
        taskRepository.save(task);

        traceService.recordTaskStateChange(startupId, "run-102", AgentType.DEVELOPMENT.name(),
                task.getId(), task.getTitle(), oldStatus, TaskStatus.IN_PROGRESS, "Scheduled for execution");

        List<ExecutionTraceEntryDto> trace = traceService.getTrace(startupId);
        assertThat(trace).hasSize(2);
        assertThat(trace.get(0).type()).isEqualTo(ExecutionTraceType.TASK_STATE_CHANGE);
        assertThat(trace.get(0).outcome()).isEqualTo("PENDING");
        assertThat(trace.get(1).type()).isEqualTo(ExecutionTraceType.TASK_STATE_CHANGE);
        assertThat(trace.get(1).outcome()).isEqualTo("IN_PROGRESS");
    }

    @Test
    @DisplayName("C. No trace entry is created when status remains unchanged")
    void testNoTraceEntryWhenStatusUnchanged() {
        Long startupId = 103L;

        traceService.recordTaskStateChange(startupId, "run-103", AgentType.FINANCE.name(),
                1L, "Budget Allocation", TaskStatus.IN_PROGRESS, TaskStatus.IN_PROGRESS, "No change");

        List<ExecutionTraceEntryDto> trace = traceService.getTrace(startupId);
        assertThat(trace).isEmpty();
    }

    @Test
    @DisplayName("D. Multiple trace types maintain correct sequence ordering")
    void testMultipleTraceTypesMaintainSequenceOrdering() {
        Long startupId = 104L;

        traceService.recordAgentAction(startupId, null, AgentType.CEO, "ANALYSIS", "Ceo Start", "openai");
        traceService.recordLLMReasoning(startupId, null, AgentType.CEO, "STRATEGIC_ANALYSIS", "Strategic vision", Map.of("provider", "openai"));
        traceService.recordTaskStateChange(startupId, null, AgentType.CEO.name(), 1L, "Kickoff", null, TaskStatus.PENDING, "Created");
        traceService.recordToolCall(startupId, null, AgentType.DEVELOPMENT, "eval", Map.of());
        traceService.recordToolResult(startupId, null, AgentType.DEVELOPMENT, "eval", true, "ok");
        traceService.recordAgentMessage(startupId, null, AgentType.DEVELOPMENT, AgentType.FINANCE, "Budget check", "Cloud cost");
        traceService.recordMemoryRetrieval(startupId, null, AgentType.FINANCE, "STARTUP_SHARED", 1);
        traceService.recordMemoryCreation(startupId, null, AgentType.FINANCE, "AGENT_PRIVATE", "HIGH", "Memory note");
        traceService.recordOrchestrationDecision(startupId, null, AgentType.CEO, "RUN_AGENT", "Run Dev", "Run Dev", true, null);
        traceService.recordReplan(startupId, null, "Dev task", "REPLAN", "Add task", true);
        traceService.recordRecovery(startupId, null, AgentType.DEVELOPMENT, "HANDOFF", AgentType.FINANCE, "HANDOFF_SUCCESS");
        traceService.recordHandoff(startupId, null, AgentType.DEVELOPMENT, AgentType.FINANCE, "Handoff reason", "payload");
        traceService.recordFailure(startupId, null, AgentType.DEVELOPMENT, "BUILD", "Error");

        List<ExecutionTraceEntryDto> trace = traceService.getTrace(startupId);
        assertThat(trace).hasSize(13);

        for (int i = 0; i < trace.size() - 1; i++) {
            assertThat(trace.get(i).sequenceNumber()).isLessThan(trace.get(i + 1).sequenceNumber());
            assertThat(trace.get(i).sequenceNumber()).isEqualTo(i + 1);
        }
    }

    @Test
    @DisplayName("E. Trace entries remain isolated between startups")
    void testTraceEntriesRemainIsolatedBetweenStartups() {
        Long startupA = 201L;
        Long startupB = 202L;

        traceService.recordAgentAction(startupA, null, AgentType.CEO, "ACTION_A", "Outcome A", "mock");
        traceService.recordAgentAction(startupB, null, AgentType.DEVELOPMENT, "ACTION_B", "Outcome B", "mock");

        List<ExecutionTraceEntryDto> traceA = traceService.getTrace(startupA);
        List<ExecutionTraceEntryDto> traceB = traceService.getTrace(startupB);

        assertThat(traceA).hasSize(1);
        assertThat(traceA.get(0).agent()).isEqualTo("CEO");

        assertThat(traceB).hasSize(1);
        assertThat(traceB.get(0).agent()).isEqualTo("DEVELOPMENT");
    }

    @Test
    @DisplayName("F. REAL runtime produces actual trace entries")
    void testRealRuntimeProducesActualTraceEntries() {
        Long startupId = 301L;

        CeoAgent ceoAgent = mock(CeoAgent.class);
        when(ceoAgent.usesRealLlm()).thenReturn(true);
        when(ceoAgent.runAnalysis(any())).thenReturn(new CeoAnalysisOutcome(null, "openai", true, false, null, "Ceo strategic vision"));

        DeveloperAgent devAgent = mock(DeveloperAgent.class);
        when(devAgent.type()).thenReturn(AgentType.DEVELOPMENT);
        when(devAgent.usesRealLlm()).thenReturn(true);
        when(devAgent.runAnalysisWithMemory(any(), any())).thenReturn(new AgentAnalysisOutcome(AgentType.DEVELOPMENT, "openai", true, false, null, "Dev tech plan"));

        OrchestrationActionExecutor executor = new OrchestrationActionExecutor(
                mock(com.startupsimulator.service.AgentService.class),
                eventService,
                mock(AgentInbox.class),
                mock(com.startupsimulator.agent.AgentMemoryContextBuilder.class),
                ceoAgent, devAgent, mock(MarketingAgent.class),
                mock(FinanceAgent.class),
                mock(com.startupsimulator.orchestrator.BoardroomDebate.class),
                mock(AgentRecoveryEngine.class),
                traceService
        );

        Startup startup = new Startup("RealApp", "Real Idea");
        startup.setId(startupId);
        StartupContext ctx = new StartupContext(startup);

        executor.executeRunAgent(AgentType.CEO, ctx);
        executor.executeRunAgent(AgentType.DEVELOPMENT, ctx);

        List<ExecutionTraceEntryDto> trace = traceService.getTrace(startupId);
        assertThat(trace).extracting(ExecutionTraceEntryDto::type)
                .contains(ExecutionTraceType.LLM_REASONING, ExecutionTraceType.AGENT_ACTION);
    }

    @Test
    @DisplayName("G. SCRIPTED_DEMO remains deterministic and does not pretend LLM reasoning occurred")
    void testScriptedDemoDoesNotEmitLlmReasoning() {
        Long startupId = 401L;

        CeoAgent ceoAgent = mock(CeoAgent.class);
        when(ceoAgent.usesRealLlm()).thenReturn(false);
        when(ceoAgent.runAnalysis(any())).thenReturn(new CeoAnalysisOutcome(null, "mock", false, false, null, "Ceo mock vision"));

        OrchestrationActionExecutor executor = new OrchestrationActionExecutor(
                mock(com.startupsimulator.service.AgentService.class),
                eventService,
                mock(AgentInbox.class),
                mock(com.startupsimulator.agent.AgentMemoryContextBuilder.class),
                ceoAgent, mock(DeveloperAgent.class), mock(MarketingAgent.class),
                mock(FinanceAgent.class),
                mock(com.startupsimulator.orchestrator.BoardroomDebate.class),
                mock(AgentRecoveryEngine.class),
                traceService
        );

        Startup startup = new Startup("DemoApp", "Demo Idea");
        startup.setId(startupId);
        StartupContext ctx = new StartupContext(startup);

        executor.executeRunAgent(AgentType.CEO, ctx);

        List<ExecutionTraceEntryDto> trace = traceService.getTrace(startupId);
        assertThat(trace).extracting(ExecutionTraceEntryDto::type)
                .contains(ExecutionTraceType.AGENT_ACTION)
                .doesNotContain(ExecutionTraceType.LLM_REASONING);
    }

    @Test
    @DisplayName("H. Failure/recovery trace entries are created only when corresponding runtime event occurs")
    void testFailureAndRecoveryTraceEntriesOnlyWhenEventOccurs() {
        Long startupId = 501L;

        LLMService llm = mock(LLMService.class);
        when(llm.isRealProvider()).thenReturn(true);
        when(llm.provider()).thenReturn("openai");

        LlmProperties props = new LlmProperties();
        props.setMode(AiExecutionMode.REAL);

        AgentInbox inbox = mock(AgentInbox.class);
        AgentRecoveryEngine recoveryEngine = new AgentRecoveryEngine(llm, props, inbox, eventService, traceService);

        AgentRecoveryRequest req = new AgentRecoveryRequest(startupId, AgentType.DEVELOPMENT, "DEV_TASK", "LLM_FAILURE", "Parse error", "Context");

        when(llm.generateStructured(any(), any(), eq(com.startupsimulator.agent.recovery.AgentRecoveryResponse.class)))
                .thenReturn(new com.startupsimulator.agent.recovery.AgentRecoveryResponse(
                        com.startupsimulator.agent.recovery.AgentRecoveryStrategy.HANDOFF,
                        AgentType.FINANCE, "Cost handoff", "Check cost", "Plan"
                ));

        AgentRecoveryOutcome outcome = recoveryEngine.attemptRecovery(req);
        assertThat(outcome.handoff()).isTrue();

        List<ExecutionTraceEntryDto> trace = traceService.getTrace(startupId);
        assertThat(trace).extracting(ExecutionTraceEntryDto::type)
                .contains(ExecutionTraceType.RECOVERY, ExecutionTraceType.HANDOFF, ExecutionTraceType.LLM_REASONING);

        assertThat(trace).extracting(ExecutionTraceEntryDto::type)
                .doesNotContain(ExecutionTraceType.FAILURE);

        traceService.recordFailure(startupId, null, AgentType.DEVELOPMENT, "BUILD", "Compilation error");
        List<ExecutionTraceEntryDto> updatedTrace = traceService.getTrace(startupId);
        assertThat(updatedTrace).extracting(ExecutionTraceEntryDto::type)
                .contains(ExecutionTraceType.FAILURE);
    }
}
