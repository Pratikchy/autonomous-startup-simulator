package com.startupsimulator.agent.recovery;

import com.startupsimulator.agent.CeoAnalysisException;
import com.startupsimulator.agent.LLMService;
import com.startupsimulator.config.LlmProperties;
import com.startupsimulator.model.enums.AgentType;
import com.startupsimulator.model.enums.EventType;
import com.startupsimulator.service.AgentInbox;
import com.startupsimulator.service.EventService;
import com.startupsimulator.trace.ExecutionTraceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CA3 Section A: Agent-level failure adaptation and recovery engine.
 * When Phase 8A retries are exhausted, the engine gives the responsible agent an explicit,
 * bounded recovery opportunity via genuine LLM reasoning (RECOVER_SELF, HANDOFF, or ABORT).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentRecoveryEngine {

    private final LLMService llm;
    private final LlmProperties llmProperties;
    private final AgentInbox agentInbox;
    private final EventService eventService;
    private final ExecutionTraceService traceService;

    // Track active recovery attempts per (startupId, taskOrAction) to enforce maxAttempts = 1 (no recursion / infinite loops)
    private final Set<String> activeRecoveries = ConcurrentHashMap.newKeySet();

    public AgentRecoveryOutcome attemptRecovery(AgentRecoveryRequest request) {
        if (request == null || request.startupId() == null || request.failedAgent() == null) {
            return AgentRecoveryOutcome.failed("Invalid recovery request.");
        }

        String recoveryKey = request.startupId() + ":" + request.failedAgent() + ":" + request.taskOrAction();
        if (!activeRecoveries.add(recoveryKey)) {
            log.warn("Recovery bounded: maximum recovery attempts (1) reached for key {}", recoveryKey);
            traceService.recordFailure(request.startupId(), null, request.failedAgent(), "RECOVERY", "Bounded recovery limit reached");
            return AgentRecoveryOutcome.failed("Bounded recovery limit (1 attempt) reached for this failure.");
        }

        try {
            log.info("Agent recovery requested for startup {} (failedAgent={}, task={})",
                    request.startupId(), request.failedAgent(), request.taskOrAction());

            eventService.record(request.startupId(), EventType.AGENT_RECOVERY_REQUESTED,
                    "Agent " + request.failedAgent() + " requested failure recovery for task: " + request.taskOrAction(),
                    Map.of("agent", request.failedAgent().name(), "reason", request.failureReason() != null ? request.failureReason() : ""));

            traceService.recordRecovery(request.startupId(), null, request.failedAgent(),
                    "REQUESTED", null, "REQUESTED");

            AgentRecoveryResponse response;

            if (llmProperties.isRealMode()) {
                if (!llm.isRealProvider()) {
                    String msg = "REAL mode requires a real LLM provider for recovery, but '" + llm.provider() + "' is unavailable.";
                    log.warn("Agent recovery failed: {}", msg);
                    eventService.record(request.startupId(), EventType.AGENT_RECOVERY_FAILED,
                            "Agent recovery failed: " + msg, Map.of("agent", request.failedAgent().name(), "error", msg));
                    traceService.recordRecovery(request.startupId(), null, request.failedAgent(), "FAILED", null, "UNAVAILABLE");
                    return AgentRecoveryOutcome.failed(msg);
                }

                response = executeLlmRecovery(request);
            } else {
                // SCRIPTED_DEMO mode test double recovery
                response = executeScriptedRecovery(request);
            }

            return processRecoveryDecision(request, response);

        } finally {
            activeRecoveries.remove(recoveryKey);
        }
    }

    private AgentRecoveryResponse executeLlmRecovery(AgentRecoveryRequest request) {
        String systemPrompt = """
                You are the Recovery Subsystem for the Autonomous Virtual Startup Simulator.
                An agent has encountered a persistent execution failure after retries were exhausted.
                Evaluate the failure context and decide on an explicit recovery strategy:
                1. RECOVER_SELF: Re-prompt or adjust internal assumptions to retry the task.
                2. HANDOFF: Hand off the task/information to another department agent (CEO, DEVELOPMENT, MARKETING, FINANCE).
                3. ABORT: Unrecoverable failure.

                Return JSON matching AgentRecoveryResponse schema:
                {
                  "strategy": "RECOVER_SELF" | "HANDOFF" | "ABORT",
                  "targetAgent": "CEO" | "DEVELOPMENT" | "MARKETING" | "FINANCE" (or null if not HANDOFF),
                  "handoffReason": "Why recipient is appropriate",
                  "handoffPayload": "Specific task or information being handed off",
                  "recoveryPlan": "Actionable recovery steps"
                }
                """;

        String userPrompt = String.format("""
                Startup ID: %d
                Failed Agent: %s
                Task/Action: %s
                Failure Category: %s
                Failure Reason: %s
                Context: %s
                """, request.startupId(), request.failedAgent(), request.taskOrAction(),
                request.failureCategory(), request.failureReason(), request.previousContext());

        try {
            return llm.generateStructured(systemPrompt, userPrompt, AgentRecoveryResponse.class);
        } catch (CeoAnalysisException e) {
            log.warn("Recovery LLM call failed for startup {} ({})", request.startupId(), e.getMessage());
            throw e;
        }
    }

    private AgentRecoveryResponse executeScriptedRecovery(AgentRecoveryRequest request) {
        // Deterministic recovery logic for test doubles / scripted demo
        if (request.failureReason() != null && request.failureReason().toLowerCase().contains("finance")) {
            return new AgentRecoveryResponse(
                    AgentRecoveryStrategy.HANDOFF,
                    AgentType.FINANCE,
                    "Handing off financial feasibility validation to Finance agent",
                    "Please evaluate budget allocation for " + request.taskOrAction(),
                    "Handed off to Finance"
            );
        }
        return new AgentRecoveryResponse(
                AgentRecoveryStrategy.RECOVER_SELF,
                null,
                null,
                null,
                "Adjusted execution constraints and recovered self"
        );
    }

    private AgentRecoveryOutcome processRecoveryDecision(AgentRecoveryRequest request, AgentRecoveryResponse response) {
        if (response == null || response.strategy() == null) {
            eventService.record(request.startupId(), EventType.AGENT_RECOVERY_FAILED,
                    "Agent recovery produced null decision.", Map.of("agent", request.failedAgent().name()));
            traceService.recordRecovery(request.startupId(), null, request.failedAgent(), "ABORT", null, "FAILED");
            return AgentRecoveryOutcome.failed("Null recovery response.");
        }

        if (llmProperties.isRealMode() && llm.isRealProvider()) {
            traceService.recordLLMReasoning(request.startupId(), null, request.failedAgent(), "RECOVERY_DECISION",
                    response.strategy().name(),
                    Map.of("strategy", response.strategy().name(), "plan", response.recoveryPlan() != null ? response.recoveryPlan() : "", "handoffReason", response.handoffReason() != null ? response.handoffReason() : ""));
        }

        switch (response.strategy()) {
            case HANDOFF -> {
                AgentType target = response.targetAgent();
                if (target == null || target == request.failedAgent()) {
                    target = AgentType.CEO; // Fallback to CEO if invalid target specified
                }

                String reason = response.handoffReason() != null ? response.handoffReason() : "Handoff from " + request.failedAgent();
                String payload = response.handoffPayload() != null ? response.handoffPayload() : request.taskOrAction();

                // 1. Create and persist real AgentMessage via AgentInbox
                agentInbox.sendMessage(request.startupId(), request.failedAgent(), target, "RECOVERY_HANDOFF", reason, payload);

                // 2. Emit recovery handoff events
                eventService.record(request.startupId(), EventType.AGENT_RECOVERY_HANDOFF,
                        "Agent " + request.failedAgent() + " handed off task to " + target + ": " + reason,
                        Map.of("failedAgent", request.failedAgent().name(), "targetAgent", target.name(), "reason", reason));

                traceService.recordRecovery(request.startupId(), null, request.failedAgent(), "HANDOFF", target, "HANDOFF_SUCCESS");
                traceService.recordHandoff(request.startupId(), null, request.failedAgent(), target, reason, payload);
                traceService.recordAgentMessage(request.startupId(), null, request.failedAgent(), target, "RECOVERY_HANDOFF", payload);

                return AgentRecoveryOutcome.handoff(target, reason, payload);
            }
            case RECOVER_SELF -> {
                eventService.record(request.startupId(), EventType.AGENT_RECOVERY_SUCCEEDED,
                        "Agent " + request.failedAgent() + " successfully adapted and recovered self.",
                        Map.of("agent", request.failedAgent().name(), "plan", response.recoveryPlan() != null ? response.recoveryPlan() : ""));

                traceService.recordRecovery(request.startupId(), null, request.failedAgent(), "RECOVER_SELF", null, "SUCCEEDED");
                return AgentRecoveryOutcome.recoveredSelf(response.recoveryPlan());
            }
            case ABORT -> {
                eventService.record(request.startupId(), EventType.AGENT_RECOVERY_FAILED,
                        "Agent " + request.failedAgent() + " recovery aborted.",
                        Map.of("agent", request.failedAgent().name()));

                traceService.recordRecovery(request.startupId(), null, request.failedAgent(), "ABORT", null, "ABORTED");
                return AgentRecoveryOutcome.failed("Agent recovery aborted by LLM decision.");
            }
        }
        return AgentRecoveryOutcome.failed("Unknown strategy");
    }
}
