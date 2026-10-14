package com.startupsimulator.orchestrator.decision;

import com.startupsimulator.agent.LLMService;
import com.startupsimulator.config.LlmProperties;
import com.startupsimulator.model.AgentMessage;
import com.startupsimulator.model.enums.AgentType;
import com.startupsimulator.model.enums.EventType;
import com.startupsimulator.service.EventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CA3 Phase 6A: the dynamic orchestration decision engine. Its ONLY responsibility
 * is to answer "given the current startup state, what should happen next?" by
 * (1) rendering a bounded {@link OrchestrationStateSnapshot} into a prompt,
 * (2) asking the LLM for a structured {@link OrchestrationDecision},
 * (3) validating that decision with {@link OrchestrationDecisionValidator}, and
 * (4) returning a typed {@link OrchestrationDecisionOutcome}. It does NOT execute
 * the decision, run agents, run tools, loop, replan, or create memory — those are
 * later phases (6B/7).
 *
 * <p><b>LLM proposes, Java validates.</b> In REAL mode the next step is genuinely
 * chosen by the model from the bounded state; Java only accepts or rejects it. On
 * an LLM failure, malformed output, an unknown action, an invalid agent, or an
 * action illegal for the current state the engine reports an EXPLICIT failure —
 * it never silently substitutes a fixed sequence (requirement 12). Deterministic
 * decisions exist ONLY in SCRIPTED_DEMO (requirement 13), structurally isolated in
 * {@link #scriptedDecision} and never reachable from the REAL branch.
 */
import com.startupsimulator.trace.ExecutionTraceService;

@Service
public class OrchestrationDecisionEngine {

    private static final Logger log = LoggerFactory.getLogger(OrchestrationDecisionEngine.class);

    /** Bounded history of previous decisions injected into the prompt (requirement 14). */
    public static final int MAX_ORCHESTRATION_HISTORY = 5;

    private final LLMService llm;
    private final LlmProperties llmProperties;
    private final OrchestrationDecisionValidator validator;
    private final EventService eventService;
    private final ExecutionTraceService traceService;
    private final String systemPrompt;

    public OrchestrationDecisionEngine(LLMService llm, LlmProperties llmProperties,
                                       OrchestrationDecisionValidator validator, EventService eventService) {
        this(llm, llmProperties, validator, eventService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public OrchestrationDecisionEngine(LLMService llm, LlmProperties llmProperties,
                                       OrchestrationDecisionValidator validator, EventService eventService,
                                       ExecutionTraceService traceService) {
        this.llm = llm;
        this.llmProperties = llmProperties;
        this.validator = validator;
        this.eventService = eventService;
        this.traceService = traceService;
        this.systemPrompt = loadPrompt("prompts/orchestration-system-prompt.txt");
    }

    /** Whether a genuine LLM reasoning path will run: REAL mode AND a real provider. */
    public boolean usesRealLlm() {
        return llmProperties.isRealMode() && llm.isRealProvider();
    }

    /**
     * Decide the next orchestration step for the given snapshot. Never throws. The
     * mode (not merely provider availability) decides the path.
     */
    public OrchestrationDecisionOutcome decideNext(OrchestrationStateSnapshot snapshot) {
        Long id = snapshot == null ? null : snapshot.startupId();

        if (llmProperties.isRealMode()) {
            if (!llm.isRealProvider()) {
                String msg = "REAL mode requires a configured LLM provider, but '" + llm.provider()
                        + "' cannot reach a model. No orchestration decision was produced.";
                log.warn("Orchestration decision skipped for startup {}: {}", id, msg);
                emitRejected(id, null, msg, true);
                return OrchestrationDecisionOutcome.noRealProvider(msg);
            }
            emitRequested(id, snapshot, true);
            OrchestrationDecision decision;
            try {
                decision = llm.generateStructured(systemPrompt, buildUserPrompt(snapshot), OrchestrationDecision.class);
            } catch (RuntimeException e) {
                // REAL mode: an LLM/transport/parse failure is an EXPLICIT failure.
                // We do NOT substitute a deterministic decision (requirement 12).
                String err = shortError(e);
                log.warn("Orchestration decision LLM call failed for startup {} ({}); explicit failure, no fallback.",
                        id, err);
                emitRejected(id, null, "LLM failure: " + err, true);
                return OrchestrationDecisionOutcome.llmFailure(err);
            }
            return finish(id, decision, snapshot, true);
        }

        // SCRIPTED_DEMO: deterministic, zero LLM calls, isolated from REAL mode.
        emitRequested(id, snapshot, false);
        OrchestrationDecision decision = scriptedDecision(snapshot);
        return finish(id, decision, snapshot, false);
    }

    /** Validate the (LLM- or deterministically-) produced decision and emit the verdict. */
    private OrchestrationDecisionOutcome finish(Long id, OrchestrationDecision decision,
                                                OrchestrationStateSnapshot snapshot, boolean usedRealLlm) {
        OrchestrationValidation v = validator.validate(decision, snapshot);
        if (!v.valid()) {
            emitRejected(id, decision, v.reason(), usedRealLlm);
            if (traceService != null && id != null) {
                traceService.recordOrchestrationDecision(id, null, decision != null ? parseAgentType(decision.targetAgent()) : null,
                        decision != null ? decision.action() : "REJECTED",
                        decision != null ? decision.reason() : v.reason(),
                        v.reason(), false, v.reason());
            }
            return OrchestrationDecisionOutcome.rejected(decision, v.reason(), usedRealLlm);
        }
        String headline = describe(v);
        emitAccepted(id, decision, v, usedRealLlm);
        if (traceService != null && id != null) {
            if (usedRealLlm) {
                traceService.recordLLMReasoning(id, null, "ORCHESTRATOR", "ORCHESTRATION_DECISION",
                        decision != null ? decision.reasonOrEmpty() : headline,
                        Map.of("action", v.action().name(), "targetAgent", v.resolvedAgent() != null ? v.resolvedAgent().name() : "", "rationale", decision != null ? decision.rationaleOrEmpty() : headline));
            }
            traceService.recordOrchestrationDecision(id, null, v.resolvedAgent(),
                    v.action().name(),
                    decision != null ? decision.reason() : headline,
                    decision != null ? decision.rationale() : headline,
                    true, null);
        }
        return OrchestrationDecisionOutcome.accepted(decision, v.action(), v.resolvedAgent(), headline, usedRealLlm);
    }

    // ---- SCRIPTED_DEMO deterministic policy (never reachable in REAL mode) ----

    /**
     * The ONLY deterministic decision policy, confined to SCRIPTED_DEMO
     * (requirement 13): run the next pending department, else convene the debate
     * once all analyses are in, else complete. It produces a valid typed decision
     * with zero LLM calls and never leaks into REAL mode.
     */
    private OrchestrationDecision scriptedDecision(OrchestrationStateSnapshot snapshot) {
        if (!snapshot.pendingAgents().isEmpty()) {
            AgentType next = snapshot.pendingAgents().get(0);
            return OrchestrationDecision.runAgent(next.name(),
                    "Scripted demo: run the next pending department.",
                    "Deterministic SCRIPTED_DEMO policy — " + next.name()
                            + " is the next pending analysis.");
        }
        if (snapshot.debatePreconditionsMet()) {
            return OrchestrationDecision.startDebate(
                    "Scripted demo: all analyses complete, convene the debate.",
                    "Deterministic SCRIPTED_DEMO policy — all four analyses are present.");
        }
        return OrchestrationDecision.completeAnalysis(
                "Scripted demo: finalize the simulation.",
                "Deterministic SCRIPTED_DEMO policy — debate complete, nothing pending.");
    }

    // ---- Prompt rendering ---------------------------------------------------

    /** The seven bounded, labelled sections (requirement 6). */
    String buildUserPrompt(OrchestrationStateSnapshot s) {
        StringBuilder sb = new StringBuilder();

        sb.append("=== STARTUP CONTEXT ===\n");
        sb.append("Startup id: ").append(s.startupId()).append('\n');
        sb.append("Current phase: ").append(s.currentPhase()).append("\n\n");

        sb.append("=== COMPLETED AGENT ANALYSES ===\n");
        if (s.recentAnalyses().isEmpty()) {
            sb.append("(no department analysis has completed yet)\n");
        } else {
            for (OrchestrationStateSnapshot.CompletedAnalysis a : s.recentAnalyses()) {
                sb.append("- ").append(a.agent().getDisplayName()).append(": ")
                        .append(a.headline() == null ? "" : a.headline().trim()).append('\n');
            }
        }
        sb.append('\n');

        sb.append("=== RECENT AGENT MESSAGES ===\n");
        if (s.recentMessages().isEmpty()) {
            sb.append("(no recent inter-department messages)\n");
        } else {
            for (AgentMessage m : s.recentMessages()) {
                sb.append("- ").append(m.getAgentType() == null ? "?" : m.getAgentType().getDisplayName())
                        .append(" → ")
                        .append(m.getTargetAgent() == null ? "(unaddressed)" : m.getTargetAgent().getDisplayName());
                if (m.getSubject() != null && !m.getSubject().isBlank()) {
                    sb.append(" — ").append(m.getSubject().trim());
                }
                sb.append(": ").append(m.getContent() == null ? "" : m.getContent().trim()).append('\n');
            }
        }
        sb.append('\n');

        // Memory block (requirement 8): already rendered by AgentMemoryContextBuilder,
        // which supplies its own "=== RELEVANT PERSISTENT MEMORY ===" header.
        String memory = s.relevantMemory();
        if (memory == null || memory.isBlank()) {
            sb.append("=== RELEVANT PERSISTENT MEMORY ===\n");
            sb.append("No relevant persistent memory was found.\n");
        } else {
            sb.append(memory.strip()).append('\n');
        }
        sb.append('\n');

        sb.append("=== CURRENT SIMULATION STATE ===\n");
        sb.append("Completed agents: ").append(names(s.completedAgents())).append('\n');
        sb.append("Pending agents: ").append(names(s.pendingAgents())).append('\n');
        sb.append("Debate: ").append(s.debateComplete() ? "COMPLETE"
                : s.debateStarted() ? "IN_PROGRESS" : "NOT_STARTED").append('\n');
        sb.append("Execution: ").append(s.executionStatus()).append("\n\n");

        sb.append("=== AVAILABLE NEXT ACTIONS ===\n");
        appendAvailableActions(sb, s);
        sb.append('\n');

        sb.append("=== PREVIOUS ORCHESTRATION DECISIONS ===\n");
        List<OrchestrationDecision> history = boundedHistory(s.previousDecisions());
        if (history.isEmpty()) {
            sb.append("(none yet)\n");
        } else {
            for (OrchestrationDecision d : history) {
                sb.append("- ").append(d.action());
                if (d.trimmedTarget() != null && !d.trimmedTarget().isBlank()) {
                    sb.append(" ").append(d.trimmedTarget());
                }
                if (!d.reasonOrEmpty().isEmpty()) {
                    sb.append(" — ").append(d.reasonOrEmpty());
                }
                sb.append('\n');
            }
        }
        sb.append('\n');

        sb.append("Decide the single next step now and return ONLY the JSON decision object.");
        return sb.toString();
    }

    private void appendAvailableActions(StringBuilder sb, OrchestrationStateSnapshot s) {
        boolean any = false;
        if (!s.legalRunAgentTargets().isEmpty()) {
            sb.append("- RUN_AGENT with targetAgent in ").append(names(s.legalRunAgentTargets())).append('\n');
            any = true;
        }
        if (s.debatePreconditionsMet()) {
            sb.append("- START_DEBATE (all analyses complete)\n");
            any = true;
        }
        if (s.completionAllowed()) {
            sb.append("- COMPLETE_ANALYSIS (debate complete)\n");
            any = true;
        }
        if (!any) {
            sb.append("(no actions are currently legal)\n");
        }
    }

    private List<OrchestrationDecision> boundedHistory(List<OrchestrationDecision> all) {
        if (all.size() <= MAX_ORCHESTRATION_HISTORY) {
            return all;
        }
        return all.subList(all.size() - MAX_ORCHESTRATION_HISTORY, all.size());
    }

    private static String names(List<AgentType> agents) {
        if (agents.isEmpty()) {
            return "(none)";
        }
        return agents.stream().map(AgentType::name).reduce((a, b) -> a + ", " + b).orElse("(none)");
    }

    // ---- Events (requirement 15) --------------------------------------------

    private void emitRequested(Long id, OrchestrationStateSnapshot s, boolean real) {
        if (id == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("mode", real ? "REAL" : "SCRIPTED_DEMO");
        payload.put("phase", s.currentPhase() == null ? null : s.currentPhase().name());
        payload.put("completed", s.completedAgents().stream().map(AgentType::name).toList());
        payload.put("pending", s.pendingAgents().stream().map(AgentType::name).toList());
        eventService.record(id, EventType.ORCHESTRATION_DECISION_REQUESTED,
                "Deciding the next orchestration step (" + (real ? "real LLM" : "scripted") + ").", payload);
    }

    private void emitAccepted(Long id, OrchestrationDecision d, OrchestrationValidation v, boolean real) {
        if (id == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("valid", true);
        payload.put("action", v.action().name());
        payload.put("targetAgent", v.resolvedAgent() == null ? null : v.resolvedAgent().name());
        payload.put("reason", d.reasonOrEmpty());
        payload.put("rationale", d.rationaleOrEmpty());
        payload.put("mode", real ? "REAL" : "SCRIPTED_DEMO");
        eventService.record(id, EventType.ORCHESTRATION_DECISION_ACCEPTED, describe(v), payload);
    }

    private void emitRejected(Long id, OrchestrationDecision d, String reason, boolean real) {
        if (id == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("valid", false);
        payload.put("proposedAction", d == null ? null : d.action());
        payload.put("proposedTarget", d == null ? null : d.trimmedTarget());
        payload.put("reason", reason);
        payload.put("mode", real ? "REAL" : "SCRIPTED_DEMO");
        eventService.record(id, EventType.ORCHESTRATION_DECISION_REJECTED,
                "Orchestration decision rejected: " + reason, payload);
    }

    private static String describe(OrchestrationValidation v) {
        return switch (v.action()) {
            case RUN_AGENT -> "Next: run " + v.resolvedAgent().getDisplayName() + ".";
            case START_DEBATE -> "Next: convene the boardroom debate.";
            case COMPLETE_ANALYSIS -> "Next: finalize the analysis.";
        };
    }

    private static String shortError(Exception e) {
        String msg = e.getMessage();
        if (msg == null) {
            return e.getClass().getSimpleName();
        }
        return msg.length() > 200 ? msg.substring(0, 200) : msg;
    }

    private static AgentType parseAgentType(String name) {
        if (name == null || name.isBlank()) return null;
        try {
            return AgentType.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String loadPrompt(String path) {
        try {
            return StreamUtils.copyToString(new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Could not load orchestration system prompt from {}", path, e);
            return "You are the orchestration decision engine. Decide the single next step and respond "
                    + "with one JSON object: {\"action\":...,\"targetAgent\":...,\"reason\":...,\"rationale\":...}.";
        }
    }
}
