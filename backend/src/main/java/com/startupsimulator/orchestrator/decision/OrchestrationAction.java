package com.startupsimulator.orchestrator.decision;

import java.util.Optional;

/**
 * CA3 Phase 6A: the closed, bounded set of orchestration actions the decision
 * engine may propose. Every value maps to a <b>real capability already present</b>
 * in the simulation (requirement 4) — no speculative/fake actions:
 *
 * <ul>
 *   <li>{@link #RUN_AGENT} — run one department agent's analysis
 *       ({@code StartupOrchestrator.runCeoAnalysis}/{@code runAgentAnalysis}).
 *       Requires a valid {@link com.startupsimulator.model.enums.AgentType}.</li>
 *   <li>{@link #START_DEBATE} — convene the boardroom debate
 *       ({@code BoardroomDebate.conduct}). Legal only once the debate
 *       preconditions hold.</li>
 *   <li>{@link #COMPLETE_ANALYSIS} — finalize the simulation
 *       ({@code StartupOrchestrator.finalizeSimulation}). Legal only when the
 *       existing completion semantics are satisfied.</li>
 * </ul>
 *
 * <p>The LLM proposes an action as a free-form string in its structured response;
 * Java resolves it here (strict, case-insensitive, no fuzzy coercion) and the
 * {@link OrchestrationDecisionValidator} is authoritative. An unrecognised string
 * resolves to {@link Optional#empty()} and is rejected — never silently mapped to
 * a default action.
 */
public enum OrchestrationAction {

    RUN_AGENT,
    START_DEBATE,
    COMPLETE_ANALYSIS;

    /**
     * Resolve a raw action string to a known action by exact enum-name match
     * (trimmed, case-insensitive). Returns empty for null/blank/unknown — the
     * validator turns that into an explicit rejection, never a fallback.
     */
    public static Optional<OrchestrationAction> resolve(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String s = raw.trim();
        for (OrchestrationAction a : values()) {
            if (a.name().equalsIgnoreCase(s)) {
                return Optional.of(a);
            }
        }
        return Optional.empty();
    }
}

