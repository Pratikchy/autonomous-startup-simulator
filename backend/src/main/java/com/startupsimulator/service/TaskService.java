package com.startupsimulator.service;

import com.startupsimulator.model.AgentTask;
import com.startupsimulator.model.enums.AgentType;
import com.startupsimulator.model.enums.TaskPriority;
import com.startupsimulator.model.enums.TaskStatus;
import com.startupsimulator.repository.AgentTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import com.startupsimulator.trace.ExecutionTraceService;

@Service
public class TaskService {

    private final AgentTaskRepository taskRepository;
    private final ExecutionTraceService traceService;

    public TaskService(AgentTaskRepository taskRepository) {
        this(taskRepository, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public TaskService(AgentTaskRepository taskRepository, ExecutionTraceService traceService) {
        this.taskRepository = taskRepository;
        this.traceService = traceService;
    }

    @Transactional
    public AgentTask create(Long startupId, AgentType agentType, String title, String description,
                            TaskPriority priority, String estimatedCompletion) {
        AgentTask task = new AgentTask(startupId, agentType, title, priority);
        task.setDescription(description);
        task.setEstimatedCompletion(estimatedCompletion);
        task.setStatus(TaskStatus.PENDING);
        AgentTask saved = taskRepository.save(task);
        if (traceService != null) {
            traceService.recordTaskStateChange(startupId, null,
                    agentType != null ? agentType.name() : null,
                    saved.getId(), saved.getTitle(), null, TaskStatus.PENDING, "Task created");
        }
        return saved;
    }

    @Transactional
    public AgentTask update(Long taskId, int progress, TaskStatus status) {
        AgentTask task = taskRepository.findById(taskId).orElseThrow();
        TaskStatus oldStatus = task.getStatus();
        task.setProgress(Math.max(0, Math.min(100, progress)));
        task.setStatus(status);
        AgentTask saved = taskRepository.save(task);
        if (oldStatus != status && traceService != null) {
            traceService.recordTaskStateChange(saved.getStartupId(), null,
                    saved.getAgentType() != null ? saved.getAgentType().name() : null,
                    saved.getId(), saved.getTitle(), oldStatus, status, "Task updated");
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public List<AgentTask> list(Long startupId) {
        return taskRepository.findByStartupIdOrderById(startupId);
    }
}
