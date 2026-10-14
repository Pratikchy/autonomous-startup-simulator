package com.startupsimulator.orchestrator;

import com.startupsimulator.agent.AgentAnalysisOutcome;
import com.startupsimulator.agent.AgentMemoryContextBuilder;
import com.startupsimulator.agent.CeoAgent;
import com.startupsimulator.agent.CeoAnalysisOutcome;
import com.startupsimulator.agent.DepartmentAgent;
import com.startupsimulator.agent.DeveloperAgent;
import com.startupsimulator.agent.FinanceAgent;
import com.startupsimulator.agent.MarketingAgent;
import com.startupsimulator.agent.StartupContext;
import com.startupsimulator.model.enums.AgentState;
import com.startupsimulator.model.enums.AgentType;
import com.startupsimulator.model.enums.EventType;
import com.startupsimulator.service.AgentInbox;
import com.startupsimulator.service.AgentService;
import com.startupsimulator.service.EventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CA3 Phase 6B — the single place that <em>executes</em> an orchestration action
 * that has already been proposed by the {@code OrchestrationDecisionEngine} and
 * accepted by the {@code OrchestrationDecisionValidator}. It deliberately does
 * <b>not</b> decide anything: it never asks the LLM "what next", never consults a
 * fixed order, and never chooses an agent — the caller hands it exactly the
 * accepted target/action and it runs precisely that one thing (requirement 15/8).
 *
 * <p>It reuses the existing agent execution unchanged (requirement 4):
 * <ul>
 *   <li><b>RUN_AGENT → CEO</b> runs the real/fallback {@link CeoAgent} analysis
 *       (plain — the CEO has no memory-aware variant; its memory is surfaced to
 *       the orchestration prompt by the snapshot factory's CEO perspective);</li>
 *   <li><b>RUN_AGENT → department</b> delivers the department's persisted inbox,
 *       runs the <em>memory-aware</em> analysis ({@link DepartmentAgent#runAnalysisWithMemory})
 *       so communication and persistent memory genuinely influence it and new
 *       memory is created on success (requirement 29/17/18), then dispatches any
 *       message the agent chose to send (persisted as a real {@code AgentMessage}
 *       so the NEXT snapshot sees it);</li>
 *   <li><b>START_DEBATE</b> delegates to the existing {@code BoardroomDebate}.</li>
 * </ul>
 * The orchestrator keeps {@code COMPLETE_ANALYSIS} (finalization) because that is
 * a startup-lifecycle concern, not an agent/debate execution. The business tools
 * stay inside the agents — the executor never calls a tool directly (requirement 19).
 *
 * <p>The same path serves REAL and SCRIPTED_DEMO (requirement 14): the mode only
 * changes what each agent does internally, never how the executor runs it.
 */
import com.startupsimulator.agent.recovery.AgentRecoveryEngine;
import com.startupsimulator.agent.recovery.AgentRecoveryRequest;
import com.startupsimulator.trace.ExecutionTraceService;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
@Component
public class OrchestrationActionExecutor {

    private final AgentService agentService;
    private final EventService eventService;
    private final AgentInbox agentInbox;
    private final AgentMemoryContextBuilder memoryContextBuilder;

    private final CeoAgent ceoAgent;
    private final DeveloperAgent developerAgent;
    private final MarketingAgent marketingAgent;
    private final FinanceAgent financeAgent;

    private final com.startupsimulator.orchestrator.BoardroomDebate boardroomDebate;
    private final AgentRecoveryEngine recoveryEngine;
    private final ExecutionTraceService traceService;

    public OrchestrationActionExecutor(AgentService agentService, EventService eventService,
                                       AgentInbox agentInbox, AgentMemoryContextBuilder memoryContextBuilder,
                                       CeoAgent ceoAgent, DeveloperAgent developerAgent,
                                       MarketingAgent marketingAgent, FinanceAgent financeAgent,
                                       com.startupsimulator.orchestrator.BoardroomDebate boardroomDebate) {
        this(agentService, eventService, agentInbox, memoryContextBuilder, ceoAgent, developerAgent, marketingAgent, financeAgent, boardroomDebate, null, null);
    }

    @Autowired
    public OrchestrationActionExecutor(AgentService agentService, EventService eventService,
                                       AgentInbox agentInbox, AgentMemoryContextBuilder memoryContextBuilder,
                                       CeoAgent ceoAgent, DeveloperAgent developerAgent,
                                       MarketingAgent marketingAgent, FinanceAgent financeAgent,
                                       com.startupsimulator.orchestrator.BoardroomDebate boardroomDebate,
                                       AgentRecoveryEngine recoveryEngine, ExecutionTraceService traceService) {
        this.agentService = agentService;
        this.eventService = eventService;
        this.agentInbox = agentInbox;
        this.memoryContextBuilder = memoryContextBuilder;
        this.ceoAgent = ceoAgent;
        this.developerAgent = developerAgent;
        this.marketingAgent = marketingAgent;
        this.financeAgent = financeAgent;
        this.boardroomDebate = boardroomDebate;
        this.recoveryEngine = recoveryEngine;
        this.traceService = traceService;
    }

    /**
     * The result of running exactly one agent: enough for the orchestrator to
     * record progression and surface a headline, without re-deriving state.
     */
    public record AgentExecution(AgentType agent, boolean failed, String headline, String provider) { }

    // ---- RUN_AGENT: the dynamic-loop unit -----------------------------------

    /**
     * Execute the accepted {@code RUN_AGENT} for exactly {@code target} and return
     * its outcome. Runs that one agent only — it never auto-runs another
     * department afterward (requirement 8). CEO runs plain; departments run the
     * memory-aware path wrapped by inbox delivery/dispatch.
     */
    public AgentExecution executeRunAgent(AgentType target, StartupContext ctx) {
        if (target == AgentType.CEO) {
            return runCeo(ctx);
        }
        DepartmentAgent agent = departmentAgentFor(target);
        agentInbox.deliverTo(ctx, target);
        AgentExecution exec = runDepartmentInternal(agent, ctx, true);
        agentInbox.dispatchOutgoing(ctx);
        return exec;
    }

    // ---- START_DEBATE -------------------------------------------------------

    /** Execute the accepted {@code START_DEBATE} via the existing boardroom. */
    public void executeStartDebate(StartupContext ctx, double speed) throws InterruptedException {
        boardroomDebate.conduct(ctx, speed);
    }

    // ---- Plain entry points reused by the retry endpoints -------------------

    /** CEO analysis with lifecycle events (plain). Also used by the retry path. */
    public AgentExecution runCeo(StartupContext ctx) {
        Long id = ctx.startupId();
        boolean real = ceoAgent.usesRealLlm();

        agentService.setState(id, AgentType.CEO, AgentState.THINKING, "Analyzing the startup idea...");
        eventService.record(id, EventType.CEO_ANALYSIS_STARTED,
                "CEO started analyzing the startup idea.",
                agentPayload(AgentType.CEO, AgentState.THINKING, null));

        if (real) {
            agentService.setState(id, AgentType.CEO, AgentState.WORKING,
                    "Developing the startup strategy...");
            eventService.record(id, EventType.LLM_REQUEST_STARTED,
                    "CEO is developing the startup strategy...",
                    agentPayload(AgentType.CEO, AgentState.WORKING, ceoAgent.providerName()));
        }

        CeoAnalysisOutcome outcome = ceoAgent.runAnalysis(ctx);

        if (outcome.usedRealLlm()) {
            eventService.record(id, EventType.LLM_RESPONSE_RECEIVED,
                    "CEO received the strategy from the model.",
                    agentPayload(AgentType.CEO, AgentState.WORKING, outcome.provider()));
        }
        if (outcome.failed()) {
            eventService.record(id, EventType.CEO_ANALYSIS_FAILED,
                    "CEO analysis via " + outcome.provider()
                            + " failed — no analysis was produced in REAL mode. Retry available.",
                    agentPayload(AgentType.CEO, AgentState.WORKING, outcome.error()));
            if (traceService != null) {
                traceService.recordFailure(id, null, AgentType.CEO, "ANALYSIS_FAILED", outcome.error());
            }
            if (recoveryEngine != null) {
                AgentRecoveryRequest req = new AgentRecoveryRequest(
                        id, AgentType.CEO, "CEO_ANALYSIS", "LLM_FAILURE", outcome.error(),
                        ctx.summary()
                );
                recoveryEngine.attemptRecovery(req);
            }
        } else {
            eventService.record(id, EventType.CEO_ANALYSIS_COMPLETED,
                    "CEO completed the strategic analysis.",
                    agentPayload(AgentType.CEO, AgentState.WORKING, outcome.provider()));
            if (traceService != null) {
                if (outcome.usedRealLlm()) {
                    traceService.recordLLMReasoning(id, null, AgentType.CEO, "STRATEGIC_ANALYSIS", outcome.headlineMessage(),
                            Map.of("provider", outcome.provider(), "summary", outcome.headlineMessage() != null ? outcome.headlineMessage() : ""));
                }
                traceService.recordAgentAction(id, null, AgentType.CEO, "ANALYSIS", outcome.headlineMessage(), outcome.provider());
            }
        }
        return new AgentExecution(AgentType.CEO, outcome.failed(), outcome.headlineMessage(), outcome.provider());
    }

    /**
     * Plain department analysis (no inbox delivery, no memory persistence) with
     * lifecycle events — the exact behaviour the retry endpoints relied on.
     */
    public AgentExecution runDepartment(AgentType type, StartupContext ctx) {
        return runDepartmentInternal(departmentAgentFor(type), ctx, false);
    }

    // ---- internals ----------------------------------------------------------

    private AgentExecution runDepartmentInternal(DepartmentAgent agent, StartupContext ctx, boolean memoryAware) {
        Long id = ctx.startupId();
        AgentType type = agent.type();
        boolean real = agent.usesRealLlm();

        eventService.record(id, EventType.AGENT_ANALYSIS_STARTED,
                type.getDisplayName() + " started its analysis.",
                agentPayload(type, AgentState.WORKING, real ? agent.providerName() : null));

        if (real) {
            eventService.record(id, EventType.LLM_REQUEST_STARTED,
                    type.getDisplayName() + " is reasoning through the plan...",
                    agentPayload(type, AgentState.WORKING, agent.providerName()));
        }

        AgentAnalysisOutcome outcome = memoryAware
                ? agent.runAnalysisWithMemory(ctx, memoryContextBuilder)
                : agent.runAnalysis(ctx);

        if (outcome.usedRealLlm()) {
            eventService.record(id, EventType.LLM_RESPONSE_RECEIVED,
                    type.getDisplayName() + " received its analysis from the model.",
                    agentPayload(type, AgentState.WORKING, outcome.provider()));
        }
        if (outcome.failed()) {
            eventService.record(id, EventType.AGENT_ANALYSIS_FAILED,
                    type.getDisplayName() + " analysis via " + outcome.provider()
                            + " failed — no analysis was produced in REAL mode. Retry available.",
                    agentPayload(type, AgentState.WORKING, outcome.error()));
            if (traceService != null) {
                traceService.recordFailure(id, null, type, "ANALYSIS_FAILED", outcome.error());
            }
            if (recoveryEngine != null) {
                AgentRecoveryRequest req = new AgentRecoveryRequest(
                        id, type, type.name() + "_ANALYSIS", "LLM_FAILURE", outcome.error(),
                        ctx.summary()
                );
                recoveryEngine.attemptRecovery(req);
            }
        } else {
            eventService.record(id, EventType.AGENT_ANALYSIS_COMPLETED,
                    type.getDisplayName() + " completed its analysis.",
                    agentPayload(type, AgentState.WORKING, outcome.provider()));
            if (traceService != null) {
                if (outcome.usedRealLlm()) {
                    traceService.recordLLMReasoning(id, null, type, type.name() + "_ANALYSIS", outcome.headlineMessage(),
                            Map.of("provider", outcome.provider(), "summary", outcome.headlineMessage() != null ? outcome.headlineMessage() : ""));
                }
                traceService.recordAgentAction(id, null, type, "ANALYSIS", outcome.headlineMessage(), outcome.provider());
            }
        }
        return new AgentExecution(type, outcome.failed(), outcome.headlineMessage(), outcome.provider());
    }

    private DepartmentAgent departmentAgentFor(AgentType type) {
        return switch (type) {
            case DEVELOPMENT -> developerAgent;
            case MARKETING -> marketingAgent;
            case FINANCE -> financeAgent;
            default -> throw new IllegalArgumentException("Not a department agent: " + type);
        };
    }

    static Map<String, Object> agentPayload(AgentType type, AgentState state, Object extra) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("agentType", type.name());
        payload.put("agentState", state == null ? null : state.name());
        if (extra != null) {
            payload.put("detail", extra);
        }
        return payload;
    }
}
