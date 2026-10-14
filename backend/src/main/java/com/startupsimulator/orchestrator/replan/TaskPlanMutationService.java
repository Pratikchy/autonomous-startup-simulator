package com.startupsimulator.orchestrator.replan;

import com.startupsimulator.model.ExecutionTask;
import com.startupsimulator.model.enums.AgentType;
import com.startupsimulator.model.enums.EventType;
import com.startupsimulator.model.enums.TaskPriority;
import com.startupsimulator.model.enums.TaskStatus;
import com.startupsimulator.repository.ExecutionTaskRepository;
import com.startupsimulator.service.EventService;
import com.startupsimulator.trace.ExecutionTraceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * CA3 Phase 7: Applies validated {@link ReplanProposal}s to the persistent task graph
 * stored in {@link ExecutionTaskRepository} and records real audit events.
 */
@Slf4j
@Service
public class TaskPlanMutationService {

    private final ExecutionTaskRepository taskRepository;
    private final EventService eventService;
    private final ExecutionTraceService traceService;

    public TaskPlanMutationService(ExecutionTaskRepository taskRepository, EventService eventService) {
        this(taskRepository, eventService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public TaskPlanMutationService(ExecutionTaskRepository taskRepository, EventService eventService, ExecutionTraceService traceService) {
        this.taskRepository = taskRepository;
        this.eventService = eventService;
        this.traceService = traceService;
    }

    @Transactional
    public List<ExecutionTask> applyReplan(Long startupId, ReplanProposal proposal) {
        if (proposal == null || proposal.changesOrEmpty().isEmpty()) {
            return taskRepository.findByStartupIdOrderById(startupId);
        }

        log.info("Applying validated replan proposal to startup {}: {}", startupId, proposal.reasonOrEmpty());

        Map<Integer, Long> changeIdxToRealId = new HashMap<>();
        List<ReplanChange> changes = proposal.changesOrEmpty();

        for (int i = 0; i < changes.size(); i++) {
            ReplanChange change = changes.get(i);
            ReplanActionType action = change.resolvedActionType().orElse(null);
            if (action == null) continue;

            switch (action) {
                case ADD_TASK -> {
                    AgentType dept = AgentType.valueOf(change.department().trim().toUpperCase());
                    TaskPriority prio = parsePriority(change.priority());
                    ExecutionTask newTask = new ExecutionTask(
                            startupId,
                            dept,
                            dept.getDisplayName() + " Agent",
                            change.title().trim(),
                            change.description() != null ? change.description().trim() : "",
                            prio,
                            4
                    );
                    newTask.setStatus(TaskStatus.PENDING);

                    List<Long> resolvedDeps = new ArrayList<>();
                    if (change.dependencies() != null) {
                        for (Long depId : change.dependencies()) {
                            if (depId < 0) {
                                // If depId refers to a negative temp index, resolve it
                                int refIdx = (int) (-depId - 1);
                                if (changeIdxToRealId.containsKey(refIdx)) {
                                    resolvedDeps.add(changeIdxToRealId.get(refIdx));
                                }
                            } else {
                                resolvedDeps.add(depId);
                            }
                        }
                    }
                    newTask.setDependencies(resolvedDeps);
                    newTask = taskRepository.save(newTask);
                    changeIdxToRealId.put(i, newTask.getId());

                    eventService.record(startupId, EventType.TASK_CREATED,
                            "Replanning created task #" + newTask.getId() + ": " + newTask.getTitle(),
                            Map.of("taskId", newTask.getId(), "department", dept.name(), "title", newTask.getTitle()));

                    if (traceService != null) {
                        traceService.recordTaskStateChange(startupId, null, dept.name(), newTask.getId(), newTask.getTitle(), null, TaskStatus.PENDING, "Created task via replan");
                    }
                }
                case MODIFY_TASK -> {
                    taskRepository.findById(change.targetTaskId()).ifPresent(t -> {
                        if (change.title() != null && !change.title().isBlank()) {
                            t.setTitle(change.title().trim());
                        }
                        if (change.description() != null && !change.description().isBlank()) {
                            t.setDescription(change.description().trim());
                        }
                        if (change.priority() != null && !change.priority().isBlank()) {
                            t.setPriority(parsePriority(change.priority()));
                        }
                        taskRepository.save(t);
                        eventService.record(startupId, EventType.TASK_MODIFIED,
                                "Replanning modified task #" + t.getId() + ": " + t.getTitle(),
                                Map.of("taskId", t.getId(), "title", t.getTitle()));
                    });
                }
                case DEFER_TASK -> {
                    taskRepository.findById(change.targetTaskId()).ifPresent(t -> {
                        TaskStatus oldStatus = t.getStatus();
                        t.setStatus(TaskStatus.DEFERRED);
                        if (change.reason() != null && !change.reason().isBlank()) {
                            String existing = t.getBlockerReason();
                            if (existing != null && !existing.isBlank()) {
                                t.setBlockerReason(existing + " | Deferred: " + change.reason().trim());
                            } else {
                                t.setBlockerReason("Deferred: " + change.reason().trim());
                            }
                        }
                        taskRepository.save(t);
                        eventService.record(startupId, EventType.TASK_DEFERRED,
                                "Replanning deferred task #" + t.getId() + ": " + t.getTitle(),
                                Map.of("taskId", t.getId(), "status", TaskStatus.DEFERRED.name()));

                        if (traceService != null) {
                            traceService.recordTaskStateChange(startupId, null, t.getDepartment() != null ? t.getDepartment().name() : null, t.getId(), t.getTitle(), oldStatus, TaskStatus.DEFERRED, change.reason());
                        }
                    });
                }
                case CANCEL_TASK -> {
                    taskRepository.findById(change.targetTaskId()).ifPresent(t -> {
                        TaskStatus oldStatus = t.getStatus();
                        t.setStatus(TaskStatus.CANCELLED);
                        if (change.reason() != null && !change.reason().isBlank()) {
                            String existing = t.getBlockerReason();
                            if (existing != null && !existing.isBlank()) {
                                t.setBlockerReason(existing + " | Cancelled: " + change.reason().trim());
                            } else {
                                t.setBlockerReason("Cancelled: " + change.reason().trim());
                            }
                        }
                        taskRepository.save(t);
                        eventService.record(startupId, EventType.TASK_CANCELLED,
                                "Replanning cancelled task #" + t.getId() + ": " + t.getTitle(),
                                Map.of("taskId", t.getId(), "status", TaskStatus.CANCELLED.name()));

                        if (traceService != null) {
                            traceService.recordTaskStateChange(startupId, null, t.getDepartment() != null ? t.getDepartment().name() : null, t.getId(), t.getTitle(), oldStatus, TaskStatus.CANCELLED, change.reason());
                        }
                    });
                }
                case CHANGE_OWNER -> {
                    taskRepository.findById(change.targetTaskId()).ifPresent(t -> {
                        AgentType newDept = AgentType.valueOf(change.department().trim().toUpperCase());
                        t.setDepartment(newDept);
                        t.setAssignedAgent(newDept.getDisplayName() + " Agent");
                        taskRepository.save(t);
                        eventService.record(startupId, EventType.TASK_MODIFIED,
                                "Replanning reassigned task #" + t.getId() + " to " + newDept.name(),
                                Map.of("taskId", t.getId(), "department", newDept.name()));
                    });
                }
                case ADD_DEPENDENCY -> {
                    taskRepository.findById(change.targetTaskId()).ifPresent(t -> {
                        List<Long> current = new ArrayList<>(t.getDependencies());
                        Long depId = change.dependencyId();
                        if (depId != null && depId < 0) {
                            int refIdx = (int) (-depId - 1);
                            if (changeIdxToRealId.containsKey(refIdx)) {
                                depId = changeIdxToRealId.get(refIdx);
                            }
                        }
                        if (depId != null && !current.contains(depId)) {
                            current.add(depId);
                            t.setDependencies(current);
                            taskRepository.save(t);
                            eventService.record(startupId, EventType.TASK_MODIFIED,
                                    "Replanning added dependency #" + depId + " to task #" + t.getId(),
                                    Map.of("taskId", t.getId(), "addedDependency", depId));
                        }
                    });
                }
                case REMOVE_DEPENDENCY -> {
                    taskRepository.findById(change.targetTaskId()).ifPresent(t -> {
                        List<Long> current = new ArrayList<>(t.getDependencies());
                        Long depId = change.dependencyId();
                        if (current.remove(depId)) {
                            t.setDependencies(current);
                            taskRepository.save(t);
                            eventService.record(startupId, EventType.TASK_MODIFIED,
                                    "Replanning removed dependency #" + depId + " from task #" + t.getId(),
                                    Map.of("taskId", t.getId(), "removedDependency", depId));
                        }
                    });
                }
            }
        }

        eventService.record(startupId, EventType.REPLAN_ACCEPTED,
                "Adaptive replan accepted and applied to persistent task plan: " + proposal.reasonOrEmpty(),
                Map.of("reason", proposal.reasonOrEmpty(), "changeCount", changes.size()));

        return taskRepository.findByStartupIdOrderById(startupId);
    }

    private static TaskPriority parsePriority(String raw) {
        if (raw == null || raw.isBlank()) {
            return TaskPriority.MEDIUM;
        }
        for (TaskPriority p : TaskPriority.values()) {
            if (p.name().equalsIgnoreCase(raw.trim())) {
                return p;
            }
        }
        return TaskPriority.MEDIUM;
    }
}
