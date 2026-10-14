package com.startupsimulator.orchestrator.replan;

import com.startupsimulator.agent.AgentMemoryContextBuilder;
import com.startupsimulator.agent.FakeLLMService;
import com.startupsimulator.agent.StartupContext;
import com.startupsimulator.config.AiExecutionMode;
import com.startupsimulator.config.LlmProperties;
import com.startupsimulator.memory.AgentMemoryRepository;
import com.startupsimulator.memory.MemoryService;
import com.startupsimulator.model.AgentMessage;
import com.startupsimulator.model.ExecutionTask;
import com.startupsimulator.model.Startup;
import com.startupsimulator.model.enums.AgentType;
import com.startupsimulator.model.enums.SimulationPhase;
import com.startupsimulator.model.enums.TaskPriority;
import com.startupsimulator.model.enums.TaskStatus;
import com.startupsimulator.orchestrator.decision.OrchestrationDecision;
import com.startupsimulator.orchestrator.decision.OrchestrationDecisionEngine;
import com.startupsimulator.orchestrator.decision.OrchestrationDecisionOutcome;
import com.startupsimulator.orchestrator.decision.OrchestrationDecisionValidator;
import com.startupsimulator.orchestrator.decision.OrchestrationStateSnapshot;
import com.startupsimulator.orchestrator.decision.OrchestrationStateSnapshotFactory;
import com.startupsimulator.repository.AgentMessageRepository;
import com.startupsimulator.repository.ExecutionTaskRepository;
import com.startupsimulator.service.EventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * CA3 Phase 7: Comprehensive acceptance proofs for Adaptive Execution & Replanning.
 */
@DataJpaTest
class AdaptiveExecutionTest {

    private static final long STARTUP = 100L;

    @Autowired
    private ExecutionTaskRepository taskRepository;

    @Autowired
    private AgentMessageRepository messageRepository;

    @Autowired
    private AgentMemoryRepository memoryRepository;

    @Autowired
    private TestEntityManager em;

    private EventService events;
    private ReplanValidator validator;
    private TaskPlanMutationService mutationService;
    private MemoryService memoryService;
    private AgentMemoryContextBuilder memoryContextBuilder;
    private OrchestrationStateSnapshotFactory snapshotFactory;

    @BeforeEach
    void setUp() {
        events = mock(EventService.class);
        validator = new ReplanValidator();
        mutationService = new TaskPlanMutationService(taskRepository, events);
        memoryService = new MemoryService(memoryRepository, events);
        memoryContextBuilder = new AgentMemoryContextBuilder(memoryService, events);
        snapshotFactory = new OrchestrationStateSnapshotFactory(memoryContextBuilder, messageRepository);
    }

    private ReplanEngine engine(FakeLLMService llm, LlmProperties props) {
        return new ReplanEngine(llm, props, validator, mutationService, taskRepository, events);
    }

    private static LlmProperties realProps() {
        LlmProperties p = new LlmProperties();
        p.setMode(AiExecutionMode.REAL);
        return p;
    }

    private static LlmProperties scriptedProps() {
        return new LlmProperties(); // default SCRIPTED_DEMO
    }

    // ---- A. Task lifecycle test ---------------------------------------------
    @Test
    void tA_taskLifecycle_transitionsFromPendingToInProgressToCompleted() {
        ExecutionTask task = new ExecutionTask(STARTUP, AgentType.DEVELOPMENT, "Dev Lead", "Build Feature A", "Desc", TaskPriority.HIGH, 5);
        task = taskRepository.save(task);

        assertThat(task.getStatus()).isEqualTo(TaskStatus.PENDING);

        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setProgress(50.0);
        taskRepository.save(task);
        em.flush();
        em.clear();

        ExecutionTask fetched = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(fetched.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(fetched.getProgress()).isEqualTo(50.0);

        fetched.setStatus(TaskStatus.COMPLETED);
        fetched.setProgress(100.0);
        taskRepository.save(fetched);
        em.flush();
        em.clear();

        ExecutionTask finalTask = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(finalTask.getStatus()).isEqualTo(TaskStatus.COMPLETED);
    }

    // ---- B. Blocked task test -----------------------------------------------
    @Test
    void tB_blockedTask_marksStatusBlockedAndPreservesReason() {
        ExecutionTask task = new ExecutionTask(STARTUP, AgentType.DEVELOPMENT, "Dev Lead", "API Integration", "Desc", TaskPriority.HIGH, 5);
        task = taskRepository.save(task);

        ReplanEngine engine = engine(FakeLLMService.offline(), scriptedProps());
        engine.evaluateAndReplan(STARTUP, task.getId(), "Third-party API credentials missing", List.of(), null);
        em.flush();
        em.clear();

        ExecutionTask updated = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(updated.getStatus()).isIn(TaskStatus.BLOCKED, TaskStatus.DEFERRED);
        assertThat(updated.getBlockerReason()).contains("Third-party API credentials missing");
    }

    // ---- C. Replan proposal validation test ---------------------------------
    @Test
    void tC_replanValidation_acceptsValidProposal_andRejectsMalformed() {
        ExecutionTask t1 = taskRepository.save(new ExecutionTask(STARTUP, AgentType.CEO, "CEO", "Kickoff", "Desc", TaskPriority.HIGH, 3));
        ExecutionTask t2 = taskRepository.save(new ExecutionTask(STARTUP, AgentType.DEVELOPMENT, "Dev Lead", "Build MVP", "Desc", TaskPriority.HIGH, 5));

        // Valid proposal
        ReplanProposal validProposal = ReplanProposal.of(
                "Need security audit before build", 0.9d, t2.getId(),
                List.of(
                        ReplanChange.addTask("DEVELOPMENT", "Security Audit", "Audit API", "HIGH", List.of(t1.getId()), "Security check"),
                        ReplanChange.deferTask(t2.getId(), "Deferred until security audit done")
                ),
                "Protects MVP security"
        );
        ReplanValidationOutcome accepted = validator.validate(validProposal, List.of(t1, t2));
        assertThat(accepted.valid()).isTrue();

        // Malformed proposal: invalid agent name "HACKER"
        ReplanProposal invalidAgent = ReplanProposal.of(
                "Invalid agent proposal", 0.5d, t2.getId(),
                List.of(ReplanChange.addTask("HACKER", "Bad Task", "Desc", "HIGH", List.of(), "Bad")),
                "Will fail"
        );
        ReplanValidationOutcome rejectedAgent = validator.validate(invalidAgent, List.of(t1, t2));
        assertThat(rejectedAgent.valid()).isFalse();
        assertThat(rejectedAgent.reason()).contains("valid department");

        // Malformed proposal: cycle introduced
        ReplanProposal cycleProposal = ReplanProposal.of(
                "Cycle proposal", 0.5d, t2.getId(),
                List.of(ReplanChange.addDependency(t1.getId(), t2.getId(), "Creates cycle")),
                "Will fail"
        );
        // Set t2 depends on t1
        t2.setDependencies(List.of(t1.getId()));
        ReplanValidationOutcome rejectedCycle = validator.validate(cycleProposal, List.of(t1, t2));
        assertThat(rejectedCycle.valid()).isFalse();
        assertThat(rejectedCycle.reason()).contains("dependency cycle");
    }

    // ---- D. Actual plan mutation test ---------------------------------------
    @Test
    void tD_actualPlanMutation_modifiesPersistedDatabaseState() {
        ExecutionTask t1 = taskRepository.save(new ExecutionTask(STARTUP, AgentType.DEVELOPMENT, "Dev Lead", "Database Schema", "Desc", TaskPriority.HIGH, 4));
        ExecutionTask t2 = taskRepository.save(new ExecutionTask(STARTUP, AgentType.DEVELOPMENT, "Dev Lead", "API Routes", "Desc", TaskPriority.HIGH, 5));
        t2.setDependencies(List.of(t1.getId()));
        t2 = taskRepository.save(t2);

        ReplanProposal proposal = ReplanProposal.of(
                "Insert cost validation task", 0.92d, t2.getId(),
                List.of(
                        ReplanChange.addTask("FINANCE", "Cost Validation", "Audit DB cost", "HIGH", List.of(t1.getId()), "Cost audit"),
                        ReplanChange.deferTask(t2.getId(), "Wait for cost validation"),
                        ReplanChange.addDependency(t2.getId(), -1L, "API routes depend on cost validation")
                ),
                "Ensures cost sanity"
        );

        mutationService.applyReplan(STARTUP, proposal);
        em.flush();
        em.clear();

        List<ExecutionTask> mutated = taskRepository.findByStartupIdOrderById(STARTUP);
        assertThat(mutated).hasSize(3);

        ExecutionTask finTask = mutated.stream().filter(t -> t.getDepartment() == AgentType.FINANCE).findFirst().orElseThrow();
        assertThat(finTask.getTitle()).isEqualTo("Cost Validation");
        assertThat(finTask.getDependencies()).contains(t1.getId());

        ExecutionTask deferredT2 = taskRepository.findById(t2.getId()).orElseThrow();
        assertThat(deferredT2.getStatus()).isEqualTo(TaskStatus.DEFERRED);
        assertThat(deferredT2.getDependencies()).contains(finTask.getId());
    }

    // ---- E. Dependency mutation test ----------------------------------------
    @Test
    void tE_dependencyMutation_changesTaskReadiness() {
        ExecutionTask t1 = taskRepository.save(new ExecutionTask(STARTUP, AgentType.CEO, "CEO", "Vision", "Desc", TaskPriority.HIGH, 2));
        ExecutionTask t2 = taskRepository.save(new ExecutionTask(STARTUP, AgentType.DEVELOPMENT, "Dev", "Feature B", "Desc", TaskPriority.HIGH, 4));

        // t1 is executable because it has no dependencies and is PENDING
        boolean t1Executable = t1.getStatus() == TaskStatus.PENDING && t1.getDependencies().isEmpty();
        assertThat(t1Executable).isTrue();

        // Mutate t2 to depend on t1
        mutationService.applyReplan(STARTUP, ReplanProposal.of(
                "Make Feature B depend on Vision", 0.9d, t2.getId(),
                List.of(ReplanChange.addDependency(t2.getId(), t1.getId(), "Prerequisite")),
                "Updated ordering"
        ));
        em.flush();
        em.clear();

        ExecutionTask updatedT2 = taskRepository.findById(t2.getId()).orElseThrow();
        assertThat(updatedT2.getDependencies()).contains(t1.getId());

        // Since t1 is not COMPLETED yet, updatedT2 is NOT executable
        boolean t2Executable = updatedT2.getStatus() == TaskStatus.PENDING &&
                updatedT2.getDependencies().stream().allMatch(depId -> taskRepository.findById(depId).map(t -> t.getStatus() == TaskStatus.COMPLETED).orElse(false));
        assertThat(t2Executable).isFalse();

        // Mark t1 COMPLETED
        t1.setStatus(TaskStatus.COMPLETED);
        taskRepository.save(t1);
        em.flush();
        em.clear();

        // Now updatedT2 IS executable!
        boolean t2ExecutableNow = updatedT2.getStatus() == TaskStatus.PENDING &&
                updatedT2.getDependencies().stream().allMatch(depId -> taskRepository.findById(depId).map(t -> t.getStatus() == TaskStatus.COMPLETED).orElse(false));
        assertThat(t2ExecutableNow).isTrue();
    }

    // ---- F. Dynamic orchestration integration test ---------------------------
    @Test
    void tF_dynamicOrchestrationIntegration_taskBlockerTriggersReplanAndNextAction() {
        ExecutionTask devTask = taskRepository.save(new ExecutionTask(STARTUP, AgentType.DEVELOPMENT, "Dev Lead", "Build Monolith", "Desc", TaskPriority.HIGH, 6));

        ReplanEngine replanEngine = engine(FakeLLMService.offline(), scriptedProps());
        ReplanValidationOutcome outcome = replanEngine.evaluateAndReplan(
                STARTUP, devTask.getId(), "Database cost infrastructure exceeds budget limit", List.of(), null);
        em.flush();
        em.clear();

        assertThat(outcome.valid()).isTrue();

        List<ExecutionTask> tasks = taskRepository.findByStartupIdOrderById(STARTUP);
        ExecutionTask financeTask = tasks.stream().filter(t -> t.getDepartment() == AgentType.FINANCE).findFirst().orElseThrow();
        assertThat(financeTask.getStatus()).isEqualTo(TaskStatus.PENDING);

        // Orchestration snapshot built from updated tasks reflects Finance is pending
        OrchestrationStateSnapshot snapshot = snapshotFactory.build(
                newContext(), SimulationPhase.ANALYSIS,
                List.of(AgentType.CEO), List.of(AgentType.DEVELOPMENT, AgentType.MARKETING, AgentType.FINANCE),
                List.of(), false, false, "ANALYSIS_IN_PROGRESS", List.of()
        );

        FakeLLMService engineFake = FakeLLMService.returning(OrchestrationDecision.runAgent("FINANCE", "Finance must unblock dev", "Finance task was added by replan"));
        OrchestrationDecisionEngine decisionEngine = new OrchestrationDecisionEngine(engineFake, realProps(), new OrchestrationDecisionValidator(), events);

        OrchestrationDecisionOutcome decisionOutcome = decisionEngine.decideNext(snapshot);
        assertThat(decisionOutcome.isAccepted()).isTrue();
        assertThat(decisionOutcome.resolvedAgent()).isEqualTo(AgentType.FINANCE);
    }

    // ---- G. REAL-mode test --------------------------------------------------
    @Test
    void tG_realMode_consumesAndValidatesLlmReplanProposal() {
        ExecutionTask t1 = taskRepository.save(new ExecutionTask(STARTUP, AgentType.DEVELOPMENT, "Dev", "Core Engine", "Desc", TaskPriority.HIGH, 5));

        ReplanProposal llmProposal = ReplanProposal.of(
                "LLM diagnosed third-party API blocker", 0.88d, t1.getId(),
                List.of(
                        ReplanChange.addTask("FINANCE", "Audit API Licensing Costs", "Evaluate subscription costs", "HIGH", List.of(), "Licensing audit"),
                        ReplanChange.deferTask(t1.getId(), "Wait for licensing audit")
                ),
                "Resolves licensing uncertainty"
        );

        FakeLLMService realFake = FakeLLMService.returning(llmProposal);
        ReplanEngine engine = engine(realFake, realProps());

        ReplanValidationOutcome outcome = engine.evaluateAndReplan(STARTUP, t1.getId(), "Licensing blocker", List.of(), null);
        em.flush();
        em.clear();

        assertThat(realFake.structuredCalls).isEqualTo(1);
        assertThat(outcome.valid()).isTrue();

        List<ExecutionTask> tasks = taskRepository.findByStartupIdOrderById(STARTUP);
        assertThat(tasks).hasSize(2);
        assertThat(tasks.stream().anyMatch(t -> t.getDepartment() == AgentType.FINANCE && t.getTitle().contains("Licensing"))).isTrue();
    }

    // ---- H. Negative test ----------------------------------------------------
    @Test
    void tH_negativeTest_invalidProposalIsRejected_andPlanRemainsUnchanged() {
        ExecutionTask t1 = taskRepository.save(new ExecutionTask(STARTUP, AgentType.DEVELOPMENT, "Dev", "Core Engine", "Desc", TaskPriority.HIGH, 5));
        int initialSize = taskRepository.findByStartupIdOrderById(STARTUP).size();

        ReplanProposal invalidProposal = ReplanProposal.of(
                "Invalid agent proposal", 0.5d, t1.getId(),
                List.of(ReplanChange.addTask("NON_EXISTENT_DEPT", "Bad Task", "Desc", "HIGH", List.of(), "Reason")),
                "Fails"
        );

        FakeLLMService realFake = FakeLLMService.returning(invalidProposal);
        ReplanEngine engine = engine(realFake, realProps());

        ReplanValidationOutcome outcome = engine.evaluateAndReplan(STARTUP, t1.getId(), "Blocker", List.of(), null);
        em.flush();
        em.clear();

        assertThat(outcome.valid()).isFalse();
        assertThat(outcome.reason()).contains("valid department");

        // Plan remains unchanged!
        List<ExecutionTask> afterTasks = taskRepository.findByStartupIdOrderById(STARTUP);
        assertThat(afterTasks).hasSize(initialSize);
        assertThat(afterTasks.get(0).getStatus()).isEqualTo(TaskStatus.BLOCKED);
    }

    // ---- I. Cross-component test --------------------------------------------
    @Test
    void tI_crossComponent_developerBlocker_persistsMessage_mutatesPlan_andSurfacesInSnapshot() {
        ExecutionTask devTask = taskRepository.save(new ExecutionTask(STARTUP, AgentType.DEVELOPMENT, "Dev", "Payment Gateways", "Desc", TaskPriority.HIGH, 5));

        AgentMessage devToFinance = new AgentMessage(STARTUP, AgentType.DEVELOPMENT, AgentType.FINANCE,
                "Payment Blocker", "Payment gateway integration requires financial feasibility audit.");
        messageRepository.save(devToFinance);
        em.flush();
        em.clear();

        ReplanEngine replanEngine = engine(FakeLLMService.offline(), scriptedProps());
        ReplanValidationOutcome outcome = replanEngine.evaluateAndReplan(
                STARTUP, devTask.getId(), devToFinance.getContent(), List.of(devToFinance), "Shared Memory Risk");
        em.flush();
        em.clear();

        assertThat(outcome.valid()).isTrue();

        List<ExecutionTask> tasks = taskRepository.findByStartupIdOrderById(STARTUP);
        ExecutionTask financeTask = tasks.stream().filter(t -> t.getDepartment() == AgentType.FINANCE).findFirst().orElseThrow();
        assertThat(financeTask.getStatus()).isEqualTo(TaskStatus.PENDING);

        OrchestrationStateSnapshot snapshot = snapshotFactory.build(
                newContext(), SimulationPhase.ANALYSIS,
                List.of(AgentType.CEO, AgentType.DEVELOPMENT), List.of(AgentType.MARKETING, AgentType.FINANCE),
                List.of(), false, false, "ANALYSIS_IN_PROGRESS", List.of()
        );

        assertThat(snapshot.recentMessages()).extracting(AgentMessage::getContent).contains(devToFinance.getContent());
    }

    // ---- J. SCRIPTED_DEMO test -----------------------------------------------
    @Test
    void tJ_scriptedDemo_zeroLlmCalls_deterministicReplanCycle() {
        ExecutionTask devTask = taskRepository.save(new ExecutionTask(STARTUP, AgentType.DEVELOPMENT, "Dev", "Core Build", "Desc", TaskPriority.HIGH, 5));

        FakeLLMService offlineLlm = FakeLLMService.offline();
        ReplanEngine engine = engine(offlineLlm, scriptedProps());

        ReplanValidationOutcome outcome = engine.evaluateAndReplan(STARTUP, devTask.getId(), "Database cost uncertainty", List.of(), null);
        em.flush();
        em.clear();

        // ZERO LLM calls made!
        assertThat(offlineLlm.structuredCalls).isZero();
        assertThat(outcome.valid()).isTrue();

        List<ExecutionTask> tasks = taskRepository.findByStartupIdOrderById(STARTUP);
        assertThat(tasks.stream().anyMatch(t -> t.getDepartment() == AgentType.FINANCE)).isTrue();
    }

    private static StartupContext newContext() {
        Startup s = new Startup();
        s.setId(STARTUP);
        s.setName("Acme");
        s.setOriginalIdea("A SaaS tool for freelancers.");
        return new StartupContext(s);
    }
}
