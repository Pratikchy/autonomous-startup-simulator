package com.startupsimulator.orchestrator.replan;

import com.startupsimulator.agent.LLMService;
import com.startupsimulator.config.LlmProperties;
import com.startupsimulator.model.AgentMessage;
import com.startupsimulator.model.ExecutionTask;
import com.startupsimulator.model.enums.AgentType;
import com.startupsimulator.model.enums.EventType;
import com.startupsimulator.model.enums.TaskStatus;
import com.startupsimulator.repository.ExecutionTaskRepository;
import com.startupsimulator.service.EventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * CA3 Phase 7: The adaptive replanning engine. When execution encounters a blocker or
 * new requirement, it diagnoses the situation, prompts the LLM for a structured
 * {@link ReplanProposal} (or uses a deterministic proposal in SCRIPTED_DEMO), validates
 * it via {@link ReplanValidator}, and applies it to the DB via {@link TaskPlanMutationService}.
 */
import com.startupsimulator.trace.ExecutionTraceService;


@Slf4j
@Service
public class ReplanEngine {

    public static final int MAX_REPLANS_PER_SIMULATION = 5;

    private final LLMService llm;
    private final LlmProperties llmProperties;
    private final ReplanValidator validator;
    private final TaskPlanMutationService mutationService;
    private final ExecutionTaskRepository taskRepository;
    private final EventService eventService;
    private final ExecutionTraceService traceService;
    private final String systemPrompt;

    public ReplanEngine(LLMService llm, LlmProperties llmProperties, ReplanValidator validator,
                        TaskPlanMutationService mutationService, ExecutionTaskRepository taskRepository,
                        EventService eventService) {
        this(llm, llmProperties, validator, mutationService, taskRepository, eventService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ReplanEngine(LLMService llm, LlmProperties llmProperties, ReplanValidator validator,
                        TaskPlanMutationService mutationService, ExecutionTaskRepository taskRepository,
                        EventService eventService, ExecutionTraceService traceService) {
        this.llm = llm;
        this.llmProperties = llmProperties;
        this.validator = validator;
        this.mutationService = mutationService;
        this.taskRepository = taskRepository;
        this.eventService = eventService;
        this.traceService = traceService;
        this.systemPrompt = loadPrompt("prompts/replan-system-prompt.txt");
    }

    public boolean usesRealLlm() {
        return llmProperties.isRealMode() && llm.isRealProvider();
    }

    /**
     * Executes adaptive replanning for a given startup when a task is blocked or needs replanning.
     */
    public ReplanValidationOutcome evaluateAndReplan(Long startupId, Long triggeringTaskId, String blockerReason,
                                                     List<AgentMessage> recentMessages, String memoryContext) {
        eventService.record(startupId, EventType.REPLAN_REQUESTED,
                "Adaptive replan requested for task #" + triggeringTaskId + ": " + blockerReason,
                Map.of("triggeringTaskId", triggeringTaskId, "blockerReason", blockerReason == null ? "" : blockerReason));

        List<ExecutionTask> currentTasks = taskRepository.findByStartupIdOrderById(startupId);
        ExecutionTask blockedTask = currentTasks.stream()
                .filter(t -> t.getId().equals(triggeringTaskId))
                .findFirst()
                .orElse(null);

        // Mark blocked task as BLOCKED if not already marked
        if (blockedTask != null && blockedTask.getStatus() != TaskStatus.BLOCKED) {
            TaskStatus oldStatus = blockedTask.getStatus();
            blockedTask.setStatus(TaskStatus.BLOCKED);
            if (blockerReason != null && !blockerReason.isBlank()) {
                blockedTask.setBlockerReason(blockerReason);
            }
            taskRepository.save(blockedTask);
            eventService.record(startupId, EventType.TASK_BLOCKED,
                    "Task #" + blockedTask.getId() + " (" + blockedTask.getTitle() + ") marked BLOCKED: " + blockerReason,
                    Map.of("taskId", blockedTask.getId(), "blockerReason", blockerReason == null ? "" : blockerReason));
            if (traceService != null) {
                traceService.recordTaskStateChange(startupId, null,
                        blockedTask.getDepartment() != null ? blockedTask.getDepartment().name() : null,
                        blockedTask.getId(), blockedTask.getTitle(), oldStatus, TaskStatus.BLOCKED, blockerReason);
            }
            currentTasks = taskRepository.findByStartupIdOrderById(startupId);
        }

        ReplanProposal proposal;

        if (llmProperties.isRealMode()) {
            if (!llm.isRealProvider()) {
                String err = "REAL mode requires a configured LLM provider, but '" + llm.provider() + "' is unavailable. Replan aborted.";
                log.warn("Replan skipped for startup {}: {}", startupId, err);
                eventService.record(startupId, EventType.REPLAN_REJECTED, err, Map.of("reason", err));
                return ReplanValidationOutcome.rejected(err, null);
            }

            try {
                String userPrompt = buildUserPrompt(startupId, blockedTask, blockerReason, currentTasks, recentMessages, memoryContext);
                proposal = llm.generateStructured(systemPrompt, userPrompt, ReplanProposal.class);
                eventService.record(startupId, EventType.REPLAN_PROPOSED,
                        "LLM proposed adaptive replan: " + proposal.reasonOrEmpty(),
                        Map.of("reason", proposal.reasonOrEmpty(), "changeCount", proposal.changesOrEmpty().size()));
            } catch (RuntimeException e) {
                String err = "LLM replan generation failed: " + e.getMessage();
                log.warn("Replan LLM call failed for startup {}: {}", startupId, err);
                eventService.record(startupId, EventType.REPLAN_REJECTED, err, Map.of("reason", err));
                return ReplanValidationOutcome.rejected(err, null);
            }
        } else {
            // SCRIPTED_DEMO mode: zero LLM calls, deterministic replan proposal
            proposal = scriptedReplanProposal(blockedTask, blockerReason, currentTasks);
            eventService.record(startupId, EventType.REPLAN_PROPOSED,
                    "Scripted demo proposed adaptive replan: " + proposal.reasonOrEmpty(),
                    Map.of("reason", proposal.reasonOrEmpty(), "changeCount", proposal.changesOrEmpty().size()));
        }

        ReplanValidationOutcome validation = validator.validate(proposal, currentTasks);
        if (!validation.valid()) {
            log.warn("Replan proposal rejected for startup {}: {}", startupId, validation.reason());
            eventService.record(startupId, EventType.REPLAN_REJECTED,
                    "Replan proposal rejected: " + validation.reason(),
                    Map.of("reason", validation.reason()));

            if (traceService != null) {
                traceService.recordReplan(startupId, null, null, triggeringTaskId, blockerReason,
                        proposal != null ? proposal.reasonOrEmpty() : validation.reason(), false, validation.reason());
            }

            return validation;
        }

        // Apply validated mutation to persistent task graph
        mutationService.applyReplan(startupId, proposal);

        if (traceService != null) {
            if (usesRealLlm()) {
                traceService.recordLLMReasoning(startupId, null, "REPLAN_ENGINE", "ADAPTIVE_REPLAN",
                        proposal != null ? proposal.reasonOrEmpty() : "Replan accepted",
                        Map.of("triggeringTaskId", triggeringTaskId != null ? triggeringTaskId : 0L, "blockerReason", blockerReason != null ? blockerReason : ""));
            }
            traceService.recordReplan(startupId, null, null, triggeringTaskId, blockerReason,
                    proposal != null ? proposal.reasonOrEmpty() : "Replan accepted", true, null);
        }

        return validation;
    }

    /**
     * Deterministic replan proposal for SCRIPTED_DEMO (zero LLM calls).
     */
    private ReplanProposal scriptedReplanProposal(ExecutionTask blockedTask, String blockerReason, List<ExecutionTask> tasks) {
        String reason = "Scripted replan: resolve technical cost blocker by inserting Finance validation task.";
        List<ReplanChange> changes = new ArrayList<>();

        Long blockedId = blockedTask != null ? blockedTask.getId() : (tasks.isEmpty() ? 1L : tasks.get(0).getId());

        // 1. Add a new Finance validation task
        changes.add(ReplanChange.addTask(
                AgentType.FINANCE.name(),
                "Validate Financial Feasibility for Tech Stack",
                "Perform unit-economics audit and budget validation for blocked development infrastructure.",
                "HIGH",
                List.of(),
                "Required to unblock development work."
        ));

        // 2. Defer the blocked task pending financial validation
        changes.add(ReplanChange.deferTask(blockedId, "Deferred until financial validation completes."));

        // 3. Add dependency on newly added task (temp index 0 -> -1) to the blocked task once un-deferred
        changes.add(ReplanChange.addDependency(blockedId, -1L, "Blocked task now depends on Finance validation."));

        return ReplanProposal.of(reason, 0.95d, blockedId, changes, "Unblocks development by establishing financial bounds.");
    }

    private String buildUserPrompt(Long startupId, ExecutionTask blockedTask, String blockerReason,
                                   List<ExecutionTask> tasks, List<AgentMessage> messages, String memoryContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== STARTUP REPLANNING CONTEXT ===\n");
        sb.append("Startup ID: ").append(startupId).append("\n");
        if (blockedTask != null) {
            sb.append("Blocked Task: #").append(blockedTask.getId()).append(" - ").append(blockedTask.getTitle()).append("\n");
            sb.append("Assigned Department: ").append(blockedTask.getDepartment()).append("\n");
        }
        sb.append("Blocker Reason: ").append(blockerReason == null ? "None specified" : blockerReason).append("\n\n");

        sb.append("=== CURRENT PERSISTED TASK PLAN ===\n");
        for (ExecutionTask t : tasks) {
            sb.append("- Task #").append(t.getId()).append(" [").append(t.getDepartment()).append("] ")
                    .append(t.getTitle()).append(" | Status: ").append(t.getStatus())
                    .append(" | Priority: ").append(t.getPriority())
                    .append(" | Deps: ").append(t.getDependencies()).append("\n");
        }
        sb.append("\n");

        sb.append("=== RECENT INTER-DEPARTMENT MESSAGES ===\n");
        if (messages == null || messages.isEmpty()) {
            sb.append("(no recent messages)\n");
        } else {
            for (AgentMessage m : messages) {
                sb.append("- ").append(m.getAgentType()).append(" -> ").append(m.getTargetAgent())
                        .append(": ").append(m.getContent()).append("\n");
            }
        }
        sb.append("\n");

        if (memoryContext != null && !memoryContext.isBlank()) {
            sb.append(memoryContext.strip()).append("\n\n");
        }

        sb.append("Analyze the blocker and propose structured plan mutations (ADD_TASK, MODIFY_TASK, DEFER_TASK, CANCEL_TASK, CHANGE_OWNER, ADD_DEPENDENCY). Return ONLY JSON.");
        return sb.toString();
    }

    private String loadPrompt(String path) {
        try {
            return StreamUtils.copyToString(new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Could not load replan system prompt from {}: using default.", path);
            return "You are the startup replanning engine. Return a JSON object matching ReplanProposal schema: {\"reason\":...,\"confidence\":0.9,\"triggeringTaskId\":1,\"changes\":[...],\"expectedImpact\":...}";
        }
    }
}
