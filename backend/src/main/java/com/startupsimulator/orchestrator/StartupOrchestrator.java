package com.startupsimulator.orchestrator;

import com.startupsimulator.agent.*;
import com.startupsimulator.model.*;
import com.startupsimulator.model.enums.*;
import com.startupsimulator.orchestrator.decision.*;
import com.startupsimulator.orchestrator.decision.OrchestrationStateSnapshot.CompletedAnalysis;
import com.startupsimulator.dto.response.DebateDto;
import com.startupsimulator.orchestrator.replan.ReplanEngine;
import com.startupsimulator.orchestrator.replan.ReplanValidationOutcome;
import com.startupsimulator.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Drives the mock simulation timeline for a startup: ANALYSIS -> DEBATE ->
 * DECISION -> PLAN -> COMPLETED. Runs asynchronously on the simulation
 * scheduler and emits {@link StartupEvent}s at every step so the office UI
 * animates in real time. All authoritative state is written through the
 * services; the frontend only reacts to events.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StartupOrchestrator {

    private final StartupService startupService;
    private final StartupContextService contextService;
    private final AgentService agentService;
    private final TaskService taskService;
    private final MessageService messageService;
    @SuppressWarnings("unused")
    private final AgentInbox agentInbox;
    private final DebateService debateService;
    @SuppressWarnings("unused")
    private final DecisionService decisionService;
    private final EventService eventService;

    private final CeoAgent ceoAgent;
    private final DeveloperAgent developerAgent;
    private final MarketingAgent marketingAgent;
    private final FinanceAgent financeAgent;

    @SuppressWarnings("unused")
    private final BoardroomDebate boardroomDebate;

    // CA3 Phase 6A/6B: the dynamic orchestration decision layer. As of Phase 6B
    // this is the ACTUAL driver of simulate() — runDynamicOrchestration() builds a
    // snapshot, asks decideNext() "what should happen next?", validates the
    // proposal, and executes exactly that one action in a bounded loop. There is NO
    // fixed CEO→DEV→MKT→FIN order in the REAL runtime. The 6A seam
    // ({@link #proposeNextOrchestrationDecision}) is kept for independent testing.
    private final OrchestrationDecisionEngine orchestrationDecisionEngine;
    private final OrchestrationStateSnapshotFactory orchestrationSnapshotFactory;

    // CA3 Phase 6B: executes an accepted decision (and ONLY executes — it never
    // decides). The dynamic loop below hands it exactly the validated action.
    private final OrchestrationActionExecutor actionExecutor;

    // CA3 Phase 7: adaptive execution & replanning engine.
    private final ReplanEngine replanEngine;

    @Value("${app.simulation.tick-interval-ms:900}")
    private long tickIntervalMs;

    private static final List<AgentType> ORDER =
            List.of(AgentType.CEO, AgentType.DEVELOPMENT, AgentType.MARKETING, AgentType.FINANCE);

    // Concurrency guard: tracks startups with an actively running worker thread to prevent duplicate workers
    private Set<Long> activeSimulations = ConcurrentHashMap.newKeySet();

    public boolean isSimulationActive(Long startupId) {
        return activeSimulations.contains(startupId);
    }

    /**
     * CA3 Phase 6B: hard upper bound on orchestration iterations. The dynamic loop
     * is <b>never</b> {@code while(true)} (requirement 5): it decides→executes at
     * most this many times, then terminates explicitly if it has not reached
     * {@code COMPLETE_ANALYSIS}. A normal run needs 6 (four analyses + debate +
     * complete); the headroom absorbs the odd rejected/re-proposed step without
     * ever looping unboundedly.
     */
    static final int MAX_ORCHESTRATION_STEPS = 12;

    /**
     * Run the full simulation asynchronously. Safe to call once per startup;
     * a second call while a simulation has already started is ignored.
     */
    @Async("simulationScheduler")
    public void simulate(Long startupId, double speed) {
        if (!activeSimulations.add(startupId)) {
            log.info("Simulation worker already active for startup {}", startupId);
            return;
        }
        try {
            Startup startup = startupService.getStartup(startupId);
            if (startup.isSimulationCompleted() || startup.getCurrentPhase() == SimulationPhase.COMPLETED) {
                log.info("Simulation already completed for startup {}", startupId);
                return;
            }
            if (startup.isSimulationStarted()) {
                log.info("Simulation already started for startup {}", startupId);
                return;
            }
            startup.setSimulationStarted(true);
            startup.setCurrentPhase(SimulationPhase.ANALYSIS);
            startupService.save(startup);

            StartupContext ctx = contextService.buildContext(startup);
            runDynamicOrchestration(ctx, speed);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Simulation interrupted for startup {}", startupId);
        } catch (Exception e) {
            log.error("Simulation failed for startup {}", startupId, e);
        } finally {
            activeSimulations.remove(startupId);
        }
    }

    /**
     * Safely resume an interrupted simulation asynchronously after backend restart.
     * Prevents duplicate workers and avoids repeating completed steps.
     */
    @Async("simulationScheduler")
    public void resumeSimulation(Long startupId, double speed) {
        if (!activeSimulations.add(startupId)) {
            log.info("Simulation worker already active for startup {}", startupId);
            return;
        }
        try {
            Startup startup = startupService.getStartup(startupId);
            if (startup.isSimulationCompleted() || startup.getCurrentPhase() == SimulationPhase.COMPLETED) {
                log.info("Startup {} is already completed; skipping recovery.", startupId);
                return;
            }
            log.info("Resuming simulation for startup {}", startupId);
            eventService.record(startupId, EventType.ORCHESTRATION_ACTION_STARTED,
                    "Resuming interrupted simulation after backend restart.",
                    Map.of("startupId", startupId, "phase", startup.getCurrentPhase().name()));

            StartupContext ctx = contextService.buildContext(startup);
            runDynamicOrchestration(ctx, speed);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Resumed simulation interrupted for startup {}", startupId);
        } catch (Exception e) {
            log.error("Resumed simulation failed for startup {}", startupId, e);
        } finally {
            activeSimulations.remove(startupId);
        }
    }

    /**
     * CA3 Phase 6B — the bounded dynamic orchestration runtime. Each iteration:
     * <ol>
     *   <li>builds a fresh {@link OrchestrationStateSnapshot} from the <em>current</em>
     *       authoritative state (so state genuinely changes between decisions,
     *       requirement 16) plus the real persisted memory/messages;</li>
     *   <li>asks the engine for the next action (LLM proposes in REAL mode, the
     *       deterministic policy proposes in SCRIPTED_DEMO — same path either way);</li>
     *   <li>on a non-accepted outcome, terminates explicitly with the cause and
     *       stops — there is NO fixed-order fallback and nothing is fabricated
     *       (requirement 12/26/27/30);</li>
     *   <li>otherwise executes <em>exactly</em> the accepted action through the
     *       executor, records the state progression (an executed agent moves
     *       pending→completed so a repeat is illegal, requirement 20), and loops.</li>
     * </ol>
     * The loop is bounded by {@link #MAX_ORCHESTRATION_STEPS}; reaching it without
     * {@code COMPLETE_ANALYSIS} is an explicit bounded termination, not an
     * auto-finalize (requirement 28). CEO is a normal pending agent — it is NOT
     * forced first; the engine orders the work (requirement 9).
     */
    void runDynamicOrchestration(StartupContext ctx, double speed) throws InterruptedException {
        Long id = ctx.startupId();

        List<AgentType> completed = new ArrayList<>();
        List<AgentType> pending = new ArrayList<>(ORDER);              // [CEO, DEV, MKT, FIN]
        Map<AgentType, String> headlines = new EnumMap<>(AgentType.class);
        Map<AgentType, Long> taskIds = new EnumMap<>(AgentType.class);
        List<OrchestrationDecision> history = new ArrayList<>();
        boolean debateStarted = false;
        boolean debateComplete = false;
        boolean persisted = false;

        // Restore existing task state and completed agents to avoid repeating completed steps
        List<AgentTask> existingTasks = taskService.list(id);
        if (existingTasks != null) {
            for (AgentTask t : existingTasks) {
                AgentType agent = t.getAgentType();
                if (agent == null) continue;
                taskIds.put(agent, t.getId());
                if (t.getStatus() == TaskStatus.COMPLETED && t.getProgress() >= 100) {
                    if (isAgentWorkVerified(ctx, agent)) {
                        if (!completed.contains(agent)) {
                            completed.add(agent);
                        }
                        pending.remove(agent);
                        String headline = findLatestHeadlineForAgent(id, agent, t.getTitle());
                        headlines.put(agent, headline);
                    }
                } else {
                    // Partially completed or interrupted mid-step: reset agent state to IDLE so it can be re-run cleanly
                    agentService.setState(id, agent, AgentState.IDLE, "Recovered — pending analysis.");
                }
            }
        }

        // Check if debate was already completed
        List<DebateDto> debates = debateService.listDtos(id);
        if (debates != null && !debates.isEmpty()) {
            boolean anyComplete = debates.stream().anyMatch(d ->
                    d.status() == DebateStatus.RESOLVED || d.status() == DebateStatus.COMPLETED_WITH_WARNINGS);
            if (anyComplete) {
                debateStarted = true;
                debateComplete = true;
                persisted = true;
            }
        }

        for (int step = 1; step <= MAX_ORCHESTRATION_STEPS; step++) {
            List<CompletedAnalysis> recentAnalyses = completed.stream()
                    .filter(headlines::containsKey)
                    .map(a -> new CompletedAnalysis(a, headlines.get(a)))
                    .toList();

            OrchestrationStateSnapshot snapshot = orchestrationSnapshotFactory.build(
                    ctx, ctx.getStartup().getCurrentPhase(),
                    List.copyOf(completed), List.copyOf(pending), recentAnalyses,
                    debateStarted, debateComplete, executionStatus(debateStarted, debateComplete),
                    List.copyOf(history));

            OrchestrationDecisionOutcome outcome = orchestrationDecisionEngine.decideNext(snapshot);

            if (!outcome.isAccepted()) {
                // Rejected / LLM failure / no real provider — stop safely, no fallback.
                terminate(id, step, "DECISION_" + outcome.status(),
                        "Orchestration stopped: " + outcome.status()
                                + (outcome.detail() == null ? "" : " — " + outcome.detail()));
                return;
            }

            history.add(outcome.decision());
            OrchestrationAction action = outcome.action();
            emitActionStarted(id, step, action, outcome.resolvedAgent());

            switch (action) {
                case RUN_AGENT -> {
                    AgentType target = outcome.resolvedAgent();
                    ensureAgentTask(ctx, taskIds, target, speed);
                    OrchestrationActionExecutor.AgentExecution exec =
                            actionExecutor.executeRunAgent(target, ctx);
                    emitActionCompleted(id, step, action, target);
                    if (exec.failed()) {
                        // REAL-mode agent failure (requirement 33): the analysis did NOT
                        // actually succeed, so we must NOT record false progression
                        // (requirement 16) — the agent stays pending and START_DEBATE /
                        // COMPLETE_ANALYSIS stay illegal until a genuine analysis exists.
                        // The bounded loop (MAX_ORCHESTRATION_STEPS, requirement 28)
                        // prevents indefinite re-running; there is no fixed-order fallback.
                        continue;
                    }
                    headlines.put(target, exec.headline());
                    // Progression (requirement 16/20): the executed agent leaves the
                    // pending set, so a second RUN_AGENT → same agent is illegal.
                    pending.remove(target);
                    if (!completed.contains(target)) {
                        completed.add(target);
                    }
                    publishAgentHeadline(ctx, taskIds, target, exec.headline(), speed);
                }
                case START_DEBATE -> {
                    // Persist the accumulated analyses once, right before the debate
                    // reads/trims them (mirrors the pre-6B persistence point).
                    contextService.persist(ctx);
                    updateHealthAfterAnalysis(ctx.getStartup());
                    startupService.save(ctx.getStartup());
                    persisted = true;
                    eventService.record(id, EventType.MVP_UPDATED, "MVP feature set drafted.",
                            Map.of("featureCount", ctx.getMvpFeatures().size()));
                    eventService.record(id, EventType.BUDGET_UPDATED, "Initial budget modelled.",
                            Map.of("budgetRemaining", ctx.getStartup().getBudgetRemaining(),
                                    "runwayMonths", ctx.getStartup().getRunwayMonths()));

                    actionExecutor.executeStartDebate(ctx, speed);
                    debateStarted = true;
                    debateComplete = true;
                    emitActionCompleted(id, step, action, null);
                }
                case COMPLETE_ANALYSIS -> {
                    if (!persisted) {
                        contextService.persist(ctx);
                        startupService.save(ctx.getStartup());
                    }
                    finalizeSimulation(ctx);
                    emitActionCompleted(id, step, action, null);
                    return;   // normal completion
                }
            }
        }

        // Requirement 28: bound reached without COMPLETE_ANALYSIS — explicit
        // termination with useful metadata; we do NOT auto-finalize.
        terminate(id, MAX_ORCHESTRATION_STEPS, "MAX_STEPS_EXHAUSTED",
                "Orchestration reached MAX_ORCHESTRATION_STEPS (" + MAX_ORCHESTRATION_STEPS
                        + ") without a COMPLETE_ANALYSIS decision.");
    }

    private static String executionStatus(boolean debateStarted, boolean debateComplete) {
        if (debateComplete) {
            return "DEBATE_COMPLETE";
        }
        return debateStarted ? "DEBATE_IN_PROGRESS" : "ANALYSIS_IN_PROGRESS";
    }

    private void emitActionStarted(Long id, int step, OrchestrationAction action, AgentType target) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("step", step);
        payload.put("action", action.name());
        if (target != null) {
            payload.put("targetAgent", target.name());
        }
        eventService.record(id, EventType.ORCHESTRATION_ACTION_STARTED,
                "Orchestration step " + step + ": executing " + action.name()
                        + (target == null ? "" : " (" + target.getDisplayName() + ")") + ".",
                payload);
    }

    private void emitActionCompleted(Long id, int step, OrchestrationAction action, AgentType target) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("step", step);
        payload.put("action", action.name());
        if (target != null) {
            payload.put("targetAgent", target.name());
        }
        eventService.record(id, EventType.ORCHESTRATION_ACTION_COMPLETED,
                "Orchestration step " + step + ": " + action.name()
                        + (target == null ? "" : " (" + target.getDisplayName() + ")") + " completed.",
                payload);
    }

    private void terminate(Long id, int step, String reasonCode, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("step", step);
        payload.put("reasonCode", reasonCode);
        eventService.record(id, EventType.ORCHESTRATION_RUNTIME_TERMINATED, message, payload);
        log.warn("Orchestration runtime terminated for startup {} at step {}: {}", id, step, message);
    }

    /**
     * CA3 Phase 6A seam (requirement 16): ask the decision engine what the next
     * orchestration step should be, given an explicit, authoritative view of the
     * current state. This builds a bounded {@link OrchestrationStateSnapshot}
     * (memory + recent messages + the supplied progress state) and returns the
     * engine's typed {@link OrchestrationDecisionOutcome} — the LLM proposes, Java
     * validates. It does NOT execute the decision and is deliberately NOT called
     * from {@link #simulate} in this phase; the engine is wired but independently
     * testable, ready for the Phase 6B/7 autonomous loop to drive it.
     */
    public OrchestrationDecisionOutcome proposeNextOrchestrationDecision(
            StartupContext ctx, SimulationPhase phase,
            List<AgentType> completedAgents, List<AgentType> pendingAgents,
            List<CompletedAnalysis> recentAnalyses,
            boolean debateStarted, boolean debateComplete, String executionStatus,
            List<OrchestrationDecision> previousDecisions) {
        OrchestrationStateSnapshot snapshot = orchestrationSnapshotFactory.build(
                ctx, phase, completedAgents, pendingAgents, recentAnalyses,
                debateStarted, debateComplete, executionStatus, previousDecisions);
        return orchestrationDecisionEngine.decideNext(snapshot);
    }

    private StartupAgent agentFor(AgentType type) {
        return switch (type) {
            case CEO -> ceoAgent;
            case DEVELOPMENT -> developerAgent;
            case MARKETING -> marketingAgent;
            case FINANCE -> financeAgent;
        };
    }

    /**
     * Re-run just the CEO analysis against the latest startup state and persist
     * the refreshed narrative fields. Used by the retry endpoint after a failed
     * LLM analysis; safe to call after the simulation has otherwise completed.
     */
    @Async("simulationScheduler")
    public void retryCeoAnalysis(Long startupId) {
        try {
            Startup startup = startupService.getStartup(startupId);
            StartupContext ctx = contextService.buildContext(startup);
            actionExecutor.runCeo(ctx);
            startupService.save(ctx.getStartup());
        } catch (Exception e) {
            log.error("CEO analysis retry failed for startup {}", startupId, e);
        }
    }

    /**
     * Re-run a single department's analysis against the latest startup state and
     * persist the refreshed plan. Used by the department retry endpoints after a
     * failed LLM analysis; safe to call after the simulation has completed. Runs
     * asynchronously and never throws to the caller.
     */
    @Async("simulationScheduler")
    public void retryDepartmentAnalysis(Long startupId, AgentType type) {
        try {
            Startup startup = startupService.getStartup(startupId);
            StartupContext ctx = contextService.buildContext(startup);
            actionExecutor.runDepartment(type, ctx);
            contextService.persistDepartmentRetry(ctx, type);
            startupService.save(ctx.getStartup());
        } catch (Exception e) {
            log.error("{} analysis retry failed for startup {}", type, startupId, e);
        }
    }

    private String shortTitle(AgentType type) {
        return switch (type) {
            case CEO -> "Analyzing business model";
            case DEVELOPMENT -> "Defining the MVP";
            case MARKETING -> "Researching the market";
            case FINANCE -> "Calculating the budget";
        };
    }

    private TaskPriority priorityFor(AgentType type) {
        return (type == AgentType.CEO || type == AgentType.DEVELOPMENT)
                ? TaskPriority.HIGH : TaskPriority.MEDIUM;
    }

    private Map<String, Object> agentPayload(AgentType type, AgentState state, Object extra) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("agentType", type.name());
        payload.put("agentState", state == null ? null : state.name());
        if (extra != null) {
            payload.put("detail", extra);
        }
        return payload;
    }

    private void sleep(double speed) throws InterruptedException {
        long ms = (long) Math.max(60, tickIntervalMs / Math.max(0.25, speed));
        Thread.sleep(ms);
    }

    /**
     * Lazily create the headline task for {@code target} and put the agent to
     * work — exactly once per agent across the dynamic loop (idempotent via
     * {@code taskIds}). This is the UI/lifecycle scaffolding only; it never runs
     * the analysis and never decides anything.
     */
    private void ensureAgentTask(StartupContext ctx, Map<AgentType, Long> taskIds,
                                 AgentType target, double speed) throws InterruptedException {
        Long id = ctx.startupId();
        StartupAgent agent = agentFor(target);
        Long taskId = taskIds.get(target);
        if (taskId == null) {
            AgentTask task = taskService.create(id, target, shortTitle(target),
                    agent.initialObjective(ctx), priorityFor(target), "Analysis phase");
            taskId = task.getId();
            taskIds.put(target, taskId);
            eventService.record(id, EventType.TASK_CREATED,
                    target.getDisplayName() + " task: " + shortTitle(target),
                    agentPayload(target, null, shortTitle(target)));
        }
        agentService.setState(id, target, AgentState.WORKING, shortTitle(target) + "...");
        eventService.record(id, EventType.AGENT_STARTED_WORK,
                target.getDisplayName() + " started working.",
                agentPayload(target, AgentState.WORKING, shortTitle(target)));
        sleep(speed);
    }

    private boolean isAgentWorkVerified(StartupContext ctx, AgentType agent) {
        if (agent == null) return false;
        return switch (agent) {
            case CEO -> ctx.getStartup().getExecutiveSummary() != null
                    && !ctx.getStartup().getExecutiveSummary().isBlank();
            case DEVELOPMENT -> (ctx.getTechnicalPlan() != null && ctx.getTechnicalPlan().getArchitecture() != null)
                    || !ctx.getMvpFeatures().isEmpty();
            case MARKETING -> ctx.getMarketingPlan() != null
                    && ctx.getMarketingPlan().getPositioning() != null;
            case FINANCE -> ctx.getBudget() != null
                    && (ctx.getBudget().getMonthlyBurn() > 0 || ctx.getBudget().getRunwayMonths() > 0);
        };
    }

    private String findLatestHeadlineForAgent(Long startupId, AgentType agent, String defaultHeadline) {
        List<com.startupsimulator.model.AgentMessage> messages = messageService.list(startupId);
        if (messages != null) {
            for (int i = messages.size() - 1; i >= 0; i--) {
                com.startupsimulator.model.AgentMessage m = messages.get(i);
                if (m.getAgentType() == agent && m.getContent() != null && !m.getContent().isBlank()) {
                    return m.getContent();
                }
            }
        }
        return defaultHeadline;
    }

    /**
     * Complete {@code target}'s headline task and surface its analysis message to
     * the office feed, then park the agent as WAITING. Called immediately after
     * the executor has actually run the agent, so the feed reflects real work.
     */
    private void publishAgentHeadline(StartupContext ctx, Map<AgentType, Long> taskIds,
                                      AgentType target, String headline, double speed) throws InterruptedException {
        Long id = ctx.startupId();
        Long taskId = taskIds.get(target);
        if (taskId != null) {
            taskService.update(taskId, 100, TaskStatus.COMPLETED);
            eventService.record(id, EventType.AGENT_PROGRESS_UPDATED,
                    target.getDisplayName() + " progress 100%",
                    agentPayload(target, AgentState.COMPLETED, 100));
        }
        messageService.create(id, target, headline, null);
        eventService.record(id, EventType.AGENT_MESSAGE_CREATED,
                headline, agentPayload(target, AgentState.WORKING, null));
        agentService.setState(id, target, AgentState.WAITING,
                "Analysis complete — awaiting the next orchestration decision.");
        sleep(speed);
    }

    private void finalizeSimulation(StartupContext ctx) {
        Long id = ctx.startupId();
        Startup startup = ctx.getStartup();

        for (AgentType type : ORDER) {
            agentService.setState(id, type, AgentState.COMPLETED,
                    "Blueprint ready — standing by.");
            eventService.record(id, EventType.AGENT_PROGRESS_UPDATED,
                    type.getDisplayName() + " finished.",
                    agentPayload(type, AgentState.COMPLETED, 100));
        }

        startup.setMvpProgress(100);
        startup.setOverallProgress(computeOverall(startup));
        startup.setCurrentPhase(SimulationPhase.COMPLETED);
        startup.setSimulationCompleted(true);
        startupService.save(startup);

        eventService.record(id, EventType.PHASE_COMPLETED, "Planning phase complete.",
                Map.of("phase", SimulationPhase.PLAN.name()));
        eventService.record(id, EventType.BLUEPRINT_GENERATED,
                startup.getName() + " blueprint is ready for review.",
                Map.of("startupId", id, "overallProgress", startup.getOverallProgress()));
    }

    // ---- Health metric computation ------------------------------------------

    private void updateHealthAfterAnalysis(Startup startup) {
        startup.setMvpProgress(40);
        startup.setOverallProgress(computeOverall(startup));
    }

    /** Blend of the four department health signals plus MVP progress. */
    private int computeOverall(Startup s) {
        int blended = (int) Math.round(
                0.30 * s.getMvpProgress()
                        + 0.25 * s.getTechnicalFeasibility()
                        + 0.20 * s.getMarketReadiness()
                        + 0.25 * s.getFinancialHealth());
        return Math.max(0, Math.min(100, blended));
    }

    /**
     * CA3 Phase 7: Trigger adaptive execution replanning for a blocked task.
     */
    public ReplanValidationOutcome triggerReplan(
            Long startupId, Long triggeringTaskId, String blockerReason) {
        List<AgentMessage> messages = messageService.list(startupId);
        String memoryContext = contextService.buildContext(startupService.getStartup(startupId)).getStartup().getOriginalIdea();
        return replanEngine.evaluateAndReplan(startupId, triggeringTaskId, blockerReason, messages, memoryContext);
    }
}

