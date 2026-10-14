package com.startupsimulator.service;

import com.startupsimulator.model.ExecutionTask;
import com.startupsimulator.model.enums.TaskStatus;
import com.startupsimulator.repository.ExecutionTaskRepository;
import com.startupsimulator.trace.ExecutionTraceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Manages execution risk events, blockers, and resolution flows.
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
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));

        if (task.getStatus() == TaskStatus.BLOCKED) {
            TaskStatus oldStatus = task.getStatus();
            task.setStatus(TaskStatus.IN_PROGRESS);
            task.setBlockerReason(null);
            taskRepository.save(task);
            if (traceService != null) {
                traceService.recordTaskStateChange(task.getStartupId(), null,
                        task.getDepartment() != null ? task.getDepartment().name() : null,
                        task.getId(), task.getTitle(), oldStatus, TaskStatus.IN_PROGRESS, "Blocker resolved");
            }
        }
        return task;
    }
}
