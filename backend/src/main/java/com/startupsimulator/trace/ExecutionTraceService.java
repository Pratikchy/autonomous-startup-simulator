package com.startupsimulator.trace;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.startupsimulator.dto.response.ExecutionTraceEntryDto;
import com.startupsimulator.model.enums.AgentType;
import com.startupsimulator.model.enums.TaskStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Central service for recording, sequencing, and querying real-time execution trace entries.
 */
@Slf4j
@Service
public class ExecutionTraceService {

    private final ExecutionTraceRepository repository;
    private final ObjectMapper objectMapper;

    public ExecutionTraceService(ExecutionTraceRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    private Map<Long, AtomicInteger> sequenceCounters = new ConcurrentHashMap<>();

    private int nextSequenceNumber(Long startupId) {
        return sequenceCounters.computeIfAbsent(startupId, id -> {
            long existing = repository.countByStartupId(id);
            return new AtomicInteger((int) existing);
        }).incrementAndGet();
    }

    @Transactional
    public ExecutionTraceEntry recordEntry(Long startupId,
                                           String traceId,
                                           ExecutionTraceType type,
                                           String agent,
                                           String sender,
                                           String recipient,
                                           String action,
                                           String decision,
                                           String reason,
                                           String toolName,
                                           String toolArguments,
                                           String toolResult,
                                           String outcome,
                                           Map<String, Object> payloadMap) {
        if (startupId == null) {
            return null;
        }

        ExecutionTraceEntry entry = new ExecutionTraceEntry();
        entry.setStartupId(startupId);
        entry.setTraceId(traceId != null ? traceId : "run-" + startupId);
        entry.setSequenceNumber(nextSequenceNumber(startupId));
        entry.setType(type);
        entry.setAgent(agent);
        entry.setSender(sender);
        entry.setRecipient(recipient);
        entry.setAction(action);
        entry.setDecision(decision);
        entry.setReason(reason);
        entry.setToolName(toolName);
        entry.setToolArguments(toolArguments);
        entry.setToolResult(toolResult);
        entry.setOutcome(outcome);
        entry.setPayload(serialize(payloadMap));

        return repository.save(entry);
    }

    @Transactional
    public void recordAgentAction(Long startupId, String traceId, AgentType agent, String action, String outcome, Map<String, Object> details) {
        recordEntry(startupId, traceId, ExecutionTraceType.AGENT_ACTION,
                agent != null ? agent.name() : null, null, null,
                action, null, null, null, null, null, outcome, details);
    }

    @Transactional
    public void recordAgentAction(Long startupId, String traceId, AgentType agent, String action, String headline, String provider) {
        recordAgentAction(startupId, traceId, agent, action, headline,
                Map.of("headline", headline != null ? headline : "", "provider", provider != null ? provider : ""));
    }

    @Transactional
    public void recordAgentMessage(Long startupId, String traceId, AgentType sender, AgentType recipient, String subject, String content) {
        recordEntry(startupId, traceId, ExecutionTraceType.AGENT_MESSAGE,
                sender != null ? sender.name() : null,
                sender != null ? sender.name() : null,
                recipient != null ? recipient.name() : null,
                "SEND_MESSAGE", null, subject, null, null, null, "SENT",
                Map.of("subject", subject != null ? subject : "", "content", content != null ? content : ""));
    }

    @Transactional
    public void recordToolCall(Long startupId, String traceId, AgentType agent, String toolName, Map<String, Object> args) {
        recordToolCall(startupId, traceId, agent, toolName, args, "Tool call initiated");
    }

    @Transactional
    public void recordToolCall(Long startupId, String traceId, AgentType agent, String toolName, Map<String, Object> args, String reason) {
        recordEntry(startupId, traceId, ExecutionTraceType.TOOL_CALL,
                agent != null ? agent.name() : null, null, null,
                "EXECUTE_TOOL", null, reason,
                toolName, serialize(args), null, "STARTED", args != null ? args : Map.of());
    }

    @Transactional
    public void recordToolResult(Long startupId, String traceId, AgentType agent, String toolName, String status, Map<String, Object> result) {
        recordEntry(startupId, traceId, ExecutionTraceType.TOOL_RESULT,
                agent != null ? agent.name() : null, null, null,
                "TOOL_RESULT", null, "Tool execution completed",
                toolName, null, serialize(result), status, result != null ? result : Map.of());
    }

    @Transactional
    public void recordToolResult(Long startupId, String traceId, AgentType agent, String toolName, boolean success, String resultStr) {
        recordToolResult(startupId, traceId, agent, toolName, success ? "SUCCEEDED" : "FAILED",
                Map.of("result", resultStr != null ? resultStr : ""));
    }

    @Transactional
    public void recordMemoryRetrieval(Long startupId, String traceId, AgentType agent, String scope, int count) {
        recordMemoryRetrieval(startupId, traceId, agent, scope, count, "Memory retrieval");
    }

    @Transactional
    public void recordMemoryRetrieval(Long startupId, String traceId, AgentType agent, String scope, int count, String reason) {
        recordEntry(startupId, traceId, ExecutionTraceType.MEMORY_RETRIEVAL,
                agent != null ? agent.name() : null, null, null,
                "RETRIEVE_MEMORY", null, reason,
                null, null, null, "RETRIEVED",
                Map.of("scope", scope != null ? scope : "", "count", count));
    }

    @Transactional
    public void recordMemoryCreation(Long startupId, String traceId, AgentType agent, String scope, String importance, String summary) {
        recordEntry(startupId, traceId, ExecutionTraceType.MEMORY_CREATION,
                agent != null ? agent.name() : null, null, null,
                "CREATE_MEMORY", null, summary,
                null, null, null, "CREATED",
                Map.of("scope", scope != null ? scope : "", "importance", importance != null ? importance : "HIGH"));
    }

    @Transactional
    public void recordMemoryCreation(Long startupId, String traceId, AgentType agent, String memoryType, String scope, int importance, String source, Long memoryId) {
        recordEntry(startupId, traceId, ExecutionTraceType.MEMORY_CREATION,
                agent != null ? agent.name() : null, null, null,
                "CREATE_MEMORY", null, "Created memory #" + memoryId,
                null, null, null, "CREATED",
                Map.of("memoryType", memoryType != null ? memoryType : "", "scope", scope != null ? scope : "", "importance", importance, "source", source != null ? source : "", "memoryId", memoryId != null ? memoryId : 0L));
    }

    @Transactional
    public void recordOrchestrationDecision(Long startupId, String traceId, String action, AgentType targetAgent, String reason, boolean accepted) {
        recordOrchestrationDecision(startupId, traceId, targetAgent, action, reason, reason, accepted, accepted ? null : reason);
    }

    @Transactional
    public void recordOrchestrationDecision(Long startupId, String traceId, AgentType targetAgent, String action, String reason, String rationale, boolean accepted, String validationError) {
        recordEntry(startupId, traceId, ExecutionTraceType.ORCHESTRATION_DECISION,
                "ORCHESTRATOR", null, targetAgent != null ? targetAgent.name() : null,
                action, action, reason, null, null, null,
                accepted ? "ACCEPTED" : "REJECTED",
                Map.of("action", action != null ? action : "", "accepted", accepted, "rationale", rationale != null ? rationale : "", "validationError", validationError != null ? validationError : ""));
    }

    @Transactional
    public void recordReplan(Long startupId, String traceId, String triggerTask, String replanType, String changes, boolean accepted) {
        recordEntry(startupId, traceId, ExecutionTraceType.REPLAN,
                "REPLAN_ENGINE", null, null,
                replanType, replanType, changes, null, null, null,
                accepted ? "ACCEPTED" : "REJECTED",
                Map.of("triggerTask", triggerTask != null ? triggerTask : "", "changes", changes != null ? changes : ""));
    }

    @Transactional
    public void recordReplan(Long startupId, String traceId, AgentType agent, Long triggeringTaskId, String blockerReason, String proposalReason, boolean accepted, String validationError) {
        recordEntry(startupId, traceId, ExecutionTraceType.REPLAN,
                agent != null ? agent.name() : "REPLAN_ENGINE", null, null,
                "REPLAN", "ADAPTIVE_REPLAN", proposalReason, null, null, null,
                accepted ? "ACCEPTED" : "REJECTED",
                Map.of("triggeringTaskId", triggeringTaskId != null ? triggeringTaskId : 0L, "blockerReason", blockerReason != null ? blockerReason : "", "accepted", accepted, "validationError", validationError != null ? validationError : ""));
    }

    @Transactional
    public void recordRecovery(Long startupId, String traceId, AgentType agent, String strategy, AgentType targetAgent, String outcome) {
        recordEntry(startupId, traceId, ExecutionTraceType.RECOVERY,
                agent != null ? agent.name() : null,
                agent != null ? agent.name() : null,
                targetAgent != null ? targetAgent.name() : null,
                strategy, strategy, "Recovery adaptation", null, null, null, outcome,
                Map.of("strategy", strategy != null ? strategy : "", "targetAgent", targetAgent != null ? targetAgent.name() : ""));
    }

    @Transactional
    public void recordFailure(Long startupId, String traceId, AgentType agent, String operation, String error) {
        recordEntry(startupId, traceId, ExecutionTraceType.FAILURE,
                agent != null ? agent.name() : null, null, null,
                operation, null, error, null, null, null, "FAILED",
                Map.of("operation", operation != null ? operation : "", "error", error != null ? error : ""));
    }

    @Transactional
    public void recordLLMReasoning(Long startupId, String traceId, String agent, String actionContext, String summary, Map<String, Object> details) {
        // The free-text reasoning summary belongs in the TEXT-mapped `reason`
        // column, NOT in the short-label `decision` column — storing it in both
        // previously overflowed `decision` and crashed the simulation on long
        // CEO responses. `action` carries the short reasoning context label.
        recordEntry(startupId, traceId, ExecutionTraceType.LLM_REASONING,
                agent, null, null,
                actionContext, null, summary,
                null, null, null, "COMPLETED", details);
    }

    @Transactional
    public void recordLLMReasoning(Long startupId, String traceId, AgentType agent, String actionContext, String summary, Map<String, Object> details) {
        recordLLMReasoning(startupId, traceId, agent != null ? agent.name() : null, actionContext, summary, details);
    }

    @Transactional
    public void recordHandoff(Long startupId, String traceId, AgentType sender, AgentType recipient, String reason, String payload) {
        recordEntry(startupId, traceId, ExecutionTraceType.HANDOFF,
                sender != null ? sender.name() : null,
                sender != null ? sender.name() : null,
                recipient != null ? recipient.name() : null,
                "HANDOFF", null, reason,
                null, null, null, "HANDOFF_SUCCESS",
                Map.of("reason", reason != null ? reason : "", "payload", payload != null ? payload : ""));
    }

    @Transactional
    public void recordTaskStateChange(Long startupId, String traceId, String agent, Long taskId, String taskTitle, TaskStatus oldStatus, TaskStatus newStatus, String reason) {
        if (oldStatus == newStatus) {
            return;
        }
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("taskId", taskId != null ? taskId : 0L);
        payload.put("title", taskTitle != null ? taskTitle : "");
        payload.put("oldStatus", oldStatus != null ? oldStatus.name() : "");
        payload.put("newStatus", newStatus != null ? newStatus.name() : "");
        if (reason != null) {
            payload.put("reason", reason);
        }

        recordEntry(startupId, traceId, ExecutionTraceType.TASK_STATE_CHANGE,
                agent != null ? agent : "TASK_BOARD", null, null,
                "MUTATE_TASK", null, reason != null ? reason : ("Task #" + taskId + " status changed to " + (newStatus != null ? newStatus.name() : "UPDATED")),
                null, null, null, newStatus != null ? newStatus.name() : "UPDATED",
                payload);
    }

    @Transactional
    public void recordTaskStateChange(Long startupId, String traceId, Long taskId, String taskTitle, TaskStatus oldStatus, TaskStatus newStatus) {
        recordTaskStateChange(startupId, traceId, null, taskId, taskTitle, oldStatus, newStatus, null);
    }

    @Transactional(readOnly = true)
    public List<ExecutionTraceEntryDto> getTrace(Long startupId) {
        return repository.findByStartupIdOrderBySequenceNumberAsc(startupId).stream()
                .map(ExecutionTraceEntryDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ExecutionTraceEntryDto> getTraceByAgent(Long startupId, String agent) {
        if (agent == null || agent.isBlank() || "ALL".equalsIgnoreCase(agent)) {
            return getTrace(startupId);
        }
        return repository.findByStartupIdOrderBySequenceNumberAsc(startupId).stream()
                .filter(entry -> agent.equalsIgnoreCase(entry.getAgent())
                        || agent.equalsIgnoreCase(entry.getSender())
                        || agent.equalsIgnoreCase(entry.getRecipient()))
                .map(ExecutionTraceEntryDto::from)
                .toList();
    }

    private String serialize(Object object) {
        if (object == null) return null;
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            return String.valueOf(object);
        }
    }
}
