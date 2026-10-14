package com.startupsimulator.service;

import com.startupsimulator.dto.response.BlueprintDto;
import com.startupsimulator.dto.response.ExecutionStateDto;
import com.startupsimulator.dto.response.ExecutionTaskDto;
import com.startupsimulator.model.ExecutionTask;
import com.startupsimulator.model.Startup;
import com.startupsimulator.model.enums.AgentState;
import com.startupsimulator.model.enums.AgentType;
import com.startupsimulator.model.enums.SimulationPhase;
import com.startupsimulator.model.enums.TaskStatus;
import com.startupsimulator.repository.AgentRepository;
import com.startupsimulator.repository.ExecutionTaskRepository;
import com.startupsimulator.repository.StartupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.startupsimulator.trace.ExecutionTraceService;

/**
 * Core engine responsible for driving Phase 2F Autonomous Startup Execution.
 * Manages simulation clock (days/weeks), state transitions, task execution progress,
 * blocker handling, and real-time SSE broadcasts.
 */
@Service
public class ExecutionEngine {

    private final StartupRepository startupRepository;
    private final AgentRepository agentRepository;
    private final ExecutionTaskRepository taskRepository;
    private final BlueprintService blueprintService;
    private final TaskGenerator taskGenerator;
    private final TaskScheduler taskScheduler;
    private final StartupMetricsService metricsService;
    private final BlockerService blockerService;
    private final ExecutionEventService executionEventService;
    private final ExecutionTraceService traceService;

    public ExecutionEngine(StartupRepository startupRepository, AgentRepository agentRepository,
                           ExecutionTaskRepository taskRepository, BlueprintService blueprintService,
                           TaskGenerator taskGenerator, TaskScheduler taskScheduler,
                           StartupMetricsService metricsService, BlockerService blockerService,
                           ExecutionEventService executionEventService) {
        this(startupRepository, agentRepository, taskRepository, blueprintService, taskGenerator, taskScheduler, metricsService, blockerService, executionEventService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ExecutionEngine(StartupRepository startupRepository, AgentRepository agentRepository,
                           ExecutionTaskRepository taskRepository, BlueprintService blueprintService,
                           TaskGenerator taskGenerator, TaskScheduler taskScheduler,
                           StartupMetricsService metricsService, BlockerService blockerService,
                           ExecutionEventService executionEventService, ExecutionTraceService traceService) {
        this.startupRepository = startupRepository;
        this.agentRepository = agentRepository;
        this.taskRepository = taskRepository;
        this.blueprintService = blueprintService;
        this.taskGenerator = taskGenerator;
        this.taskScheduler = taskScheduler;
        this.metricsService = metricsService;
        this.blockerService = blockerService;
        this.executionEventService = executionEventService;
        this.traceService = traceService;
    }

    // In-memory simulation state trackers per startupId
    private final Map<Long, Integer> currentDays = new ConcurrentHashMap<>();
    private final Map<Long, Boolean> runningState = new ConcurrentHashMap<>();
    private final Map<Long, Boolean> pausedState = new ConcurrentHashMap<>();

    @Transactional
    public ExecutionStateDto startExecution(Long startupId) {
        Startup startup = startupRepository.findById(startupId)
                .orElseThrow(() -> new IllegalArgumentException("Startup not found: " + startupId));

        // 1. Fetch Blueprint
        BlueprintDto blueprint = blueprintService.build(startupId);

        // 2. Generate executable task graph if empty
        List<ExecutionTask> tasks = taskRepository.findByStartupIdOrderById(startupId);
        if (tasks.isEmpty()) {
            tasks = taskGenerator.generateTasks(startupId, blueprint);
        }

        // 3. Update startup phase to EXECUTION
        startup.setCurrentPhase(SimulationPhase.EXECUTION);
        startup.setSimulationStarted(true);
        startupRepository.save(startup);

        currentDays.put(startupId, 1);
        runningState.put(startupId, true);
        pausedState.put(startupId, false);

        // 4. Broadcast EXECUTION_STARTED
        executionEventService.emitExecutionStarted(startupId, tasks.size());

        // 5. Initial Scheduling Pass
        List<ExecutionTask> scheduled = taskScheduler.scheduleAvailableTasks(startupId);
        for (ExecutionTask task : scheduled) {
            updateAgentState(startupId, task.getDepartment(), AgentState.WORKING, "Working on " + task.getTitle());
            executionEventService.emitTaskAssigned(startupId, task);
            executionEventService.emitTaskStarted(startupId, task);
        }

        // Emit initial metrics
        Map<String, Object> metrics = metricsService.calculateMetrics(startupId, 1);
        executionEventService.emitMetricChanged(startupId, metrics);

        return getExecutionState(startupId);
    }

    @Transactional
    public ExecutionStateDto pauseExecution(Long startupId) {
        pausedState.put(startupId, true);
        runningState.put(startupId, false);
        executionEventService.emitExecutionPaused(startupId);
        return getExecutionState(startupId);
    }

    @Transactional
    public ExecutionStateDto resumeExecution(Long startupId) {
        pausedState.put(startupId, false);
        runningState.put(startupId, true);
        executionEventService.emitExecutionResumed(startupId);
        return getExecutionState(startupId);
    }

    @Transactional
    public ExecutionStateDto stepExecution(Long startupId) {
        int day = currentDays.getOrDefault(startupId, 1) + 1;
        currentDays.put(startupId, day);

        List<ExecutionTask> allTasks = taskRepository.findByStartupIdOrderById(startupId);
        if (allTasks.isEmpty()) {
            BlueprintDto blueprint = blueprintService.build(startupId);
            allTasks = taskGenerator.generateTasks(startupId, blueprint);
        }

        // 1. Advance in-progress tasks
        for (ExecutionTask task : allTasks) {
            if (task.getStatus() == TaskStatus.IN_PROGRESS) {
                int estDays = Math.max(1, task.getEstimatedDays());
                double dailyInc = 100.0 / estDays;
                double newProgress = Math.min(100.0, task.getProgress() + dailyInc);
                task.setProgress(newProgress);
                task.setActualDays(task.getActualDays() + 1);

                if (newProgress >= 100.0) {
                    TaskStatus oldStatus = task.getStatus();
                    task.setStatus(TaskStatus.COMPLETED);
                    taskRepository.save(task);
                    if (traceService != null) {
                        traceService.recordTaskStateChange(startupId, null,
                                task.getDepartment() != null ? task.getDepartment().name() : null,
                                task.getId(), task.getTitle(), oldStatus, TaskStatus.COMPLETED, "Task completed");
                    }
                    updateAgentState(startupId, task.getDepartment(), AgentState.IDLE, "Task completed");
                    executionEventService.emitTaskCompleted(startupId, task);
                } else {
                    taskRepository.save(task);
                    executionEventService.emitTaskProgress(startupId, task);
                }
            }
        }

        // 2. Schedule available tasks whose dependencies are newly satisfied
        List<ExecutionTask> newlyScheduled = taskScheduler.scheduleAvailableTasks(startupId);
        for (ExecutionTask task : newlyScheduled) {
            updateAgentState(startupId, task.getDepartment(), AgentState.WORKING, "Working on " + task.getTitle());
            executionEventService.emitTaskAssigned(startupId, task);
            executionEventService.emitTaskStarted(startupId, task);
        }

        // 3. Simulated Blocker check
        Optional<ExecutionTask> blockedTask = blockerService.checkForSimulatedBlockers(startupId, day);
        if (blockedTask.isPresent()) {
            ExecutionTask task = blockedTask.get();
            updateAgentState(startupId, task.getDepartment(), AgentState.BLOCKED, "Blocked: " + task.getBlockerReason());
            executionEventService.emitTaskBlocked(startupId, task);
        }

        // 4. Calculate updated metrics
        Map<String, Object> metrics = metricsService.calculateMetrics(startupId, day);
        executionEventService.emitMetricChanged(startupId, metrics);

        // 5. Check execution completion
        boolean allCompleted = allTasks.stream().allMatch(t -> t.getStatus() == TaskStatus.COMPLETED);
        if (allCompleted && !allTasks.isEmpty()) {
            runningState.put(startupId, false);
            pausedState.put(startupId, false);
            Startup startup = startupRepository.findById(startupId).orElse(null);
            if (startup != null) {
                startup.setSimulationCompleted(true);
                startupRepository.save(startup);
            }
            double overall = (double) metrics.get("overallProgress");
            executionEventService.emitExecutionCompleted(startupId, overall);
        }

        return getExecutionState(startupId);
    }

    @Transactional
    public ExecutionStateDto resolveTaskBlocker(Long startupId, Long taskId) {
        ExecutionTask task = blockerService.resolveBlocker(taskId);
        updateAgentState(startupId, task.getDepartment(), AgentState.WORKING, "Resumed work on " + task.getTitle());
        executionEventService.emitTaskStarted(startupId, task);
        return getExecutionState(startupId);
    }

    @Transactional(readOnly = true)
    public ExecutionStateDto getExecutionState(Long startupId) {
        Startup startup = startupRepository.findById(startupId)
                .orElseThrow(() -> new IllegalArgumentException("Startup not found: " + startupId));

        int day = currentDays.getOrDefault(startupId, 1);
        int week = ((day - 1) / 7) + 1;
        boolean isRunning = runningState.getOrDefault(startupId, false);
        boolean isPaused = pausedState.getOrDefault(startupId, false);

        List<ExecutionTask> tasks = taskRepository.findByStartupIdOrderById(startupId);
        List<ExecutionTaskDto> taskDtos = tasks.stream().map(ExecutionTaskDto::from).toList();

        Map<String, Object> metrics = metricsService.calculateMetrics(startupId, day);

        int activeBlockerCount = (int) tasks.stream().filter(t -> t.getStatus() == TaskStatus.BLOCKED).count();
        boolean isCompleted = !tasks.isEmpty() && tasks.stream().allMatch(t -> t.getStatus() == TaskStatus.COMPLETED);

        @SuppressWarnings("unchecked")
        Map<String, Double> departmentProgressMap = (Map<String, Double>) metrics.get("departmentProgress");

        return ExecutionStateDto.builder()
                .startupId(startupId)
                .phase(startup.getCurrentPhase())
                .day(day)
                .week(week)
                .isRunning(isRunning)
                .isPaused(isPaused)
                .isCompleted(isCompleted)
                .overallProgress((Double) metrics.get("overallProgress"))
                .mvpProgress((Double) metrics.get("mvpProgress"))
                .technicalProgress((Double) metrics.get("technicalProgress"))
                .marketReadiness((Double) metrics.get("marketReadiness"))
                .financialHealth((Double) metrics.get("financialHealth"))
                .budgetRemaining((Double) metrics.get("budgetRemaining"))
                .runwayMonths((Double) metrics.get("runwayMonths"))
                .activeBlockerCount(activeBlockerCount)
                .departmentProgress(departmentProgressMap)
                .tasks(taskDtos)
                .build();
    }

    private void updateAgentState(Long startupId, AgentType type, AgentState state, String activity) {
        agentRepository.findByStartupIdAndType(startupId, type).ifPresent(agent -> {
            agent.setState(state);
            agent.setCurrentActivity(activity);
            agentRepository.save(agent);
        });
    }
}
