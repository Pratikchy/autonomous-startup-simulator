package com.startupsimulator.service;

import com.startupsimulator.exception.ResourceNotFoundException;
import com.startupsimulator.model.ExecutionTask;
import com.startupsimulator.model.enums.TaskStatus;
import com.startupsimulator.repository.ExecutionTaskRepository;
import com.startupsimulator.trace.ExecutionTraceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Manages execution risk events, blockers, and resolution flows.
 * Explicit blocker resolution. Calendar days alone are not evidence of a risk
 * or an unmet dependency; genuine blockers remain until explicitly resolved.
 */
@Service
public class BlockerService {

    private final ExecutionTaskRepository taskRepository;
    private final ExecutionTraceService traceService;

    public BlockerService(ExecutionTaskRepository taskRepository) {
        this(taskRepository, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public BlockerService(ExecutionTaskRepository taskRepository, ExecutionTraceService traceService) {
        this.taskRepository = taskRepository;
        this.traceService = traceService;
    }

    @Transactional
    public Optional<ExecutionTask> checkForSimulatedBlockers(Long startupId, int currentDay) {
        List<ExecutionTask> inProgress = taskRepository.findByStartupIdAndStatus(startupId, TaskStatus.IN_PROGRESS);

        if (inProgress.isEmpty()) {
            return Optional.empty();
        }

        // Deterministic risk trigger condition (e.g. Day 8 or Day 18) to simulate realistic project risks
        if (currentDay == 8 || currentDay == 18) {
            ExecutionTask target = inProgress.get(0);
            TaskStatus oldStatus = target.getStatus();
            target.setStatus(TaskStatus.BLOCKED);
            String reason = target.getDepartment() + " execution blocked on Day " + currentDay +
                    ": Dependencies delayed due to technical risk review.";
            target.setBlockerReason(reason);
            taskRepository.save(target);
            if (traceService != null) {
                traceService.recordTaskStateChange(startupId, null,
                        target.getDepartment() != null ? target.getDepartment().name() : null,
                        target.getId(), target.getTitle(), oldStatus, TaskStatus.BLOCKED, reason);
            }
            return Optional.of(target);
        }

        return Optional.empty();
    }

    @Transactional
    public ExecutionTask resolveBlocker(Long taskId) {
        ExecutionTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));
        return resolveBlocker(task.getStartupId(), taskId);
    }

    @Transactional
    public ExecutionTask resolveBlocker(Long startupId, Long taskId) {
        ExecutionTask task = taskRepository.findById(taskId)
                .filter(t -> Objects.equals(t.getStartupId(), startupId))
                .orElseThrow(() -> new ResourceNotFoundException("Task not found in startup " + startupId + ": " + taskId));

        if (task.getStatus() != TaskStatus.BLOCKED) {
            throw new IllegalStateException("Task " + taskId + " is not blocked; no resolution applied.");
        }

        // Use only this startup's graph: missing/cross-startup references do not
        // satisfy dependencies, nor do deferred or cancelled prerequisites.
        Map<Long, ExecutionTask> graph = taskRepository.findByStartupIdOrderById(startupId).stream()
                .collect(Collectors.toMap(ExecutionTask::getId, t -> t));
        List<String> unmet = task.getDependencies().stream()
                .filter(id -> !graph.containsKey(id) || graph.get(id).getStatus() != TaskStatus.COMPLETED)
                .map(id -> graph.containsKey(id)
                        ? "#" + id + " " + graph.get(id).getTitle() + " (" + graph.get(id).getStatus() + ")"
                        : "#" + id + " (missing from this startup)")
                .toList();
        if (!unmet.isEmpty()) {
            throw new IllegalStateException("Cannot resolve task #" + taskId + ": complete or explicitly replan prerequisites "
                    + String.join(", ", unmet) + ". Original blocker: " + task.getBlockerReason());
        }
        boolean departmentBusy = graph.values().stream().anyMatch(other ->
                !Objects.equals(other.getId(), taskId) && other.getDepartment() == task.getDepartment()
                        && (other.getStatus() == TaskStatus.IN_PROGRESS || other.getStatus() == TaskStatus.BLOCKED));
        if (departmentBusy) {
            throw new IllegalStateException("Cannot resume task #" + taskId
                    + ": finish or resolve the other active " + task.getDepartment() + " task first.");
        }

        String originalReason = task.getBlockerReason();
        boolean artificialRisk = originalReason != null && originalReason.matches(
                task.getDepartment() + " execution blocked on Day (8|18): Dependencies delayed due to technical risk review\\.");
        String resolution = (artificialRisk
                ? "Removed legacy calendar-injected risk-review blocker on explicit request; no review prerequisite exists in the task graph. "
                : "Blocker explicitly acknowledged as resolved by caller. ")
                + "Validated completed dependencies " + task.getDependencies()
                + "; resumed without changing progress or actual days. Original blocker: " + originalReason;
        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setBlockerReason(null);
        taskRepository.save(task);
        if (traceService != null) {
            traceService.recordTaskStateChange(startupId, null,
                    task.getDepartment() != null ? task.getDepartment().name() : null,
                    task.getId(), task.getTitle(), TaskStatus.BLOCKED, TaskStatus.IN_PROGRESS, resolution);
        }
        return task;
    }
}
