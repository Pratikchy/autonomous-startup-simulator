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
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ExecutionEngine.class);

    // In-memory simulation state trackers per startupId
    private final Map<Long, Integer> currentDays = new ConcurrentHashMap<>();
    private final Map<Long, Boolean> runningState = new ConcurrentHashMap<>();
    private final Map<Long, Boolean> pausedState = new ConcurrentHashMap<>();
    private final Set<Long> activeExecutionWorkers = ConcurrentHashMap.newKeySet();

    @org.springframework.beans.factory.annotation.Value("${app.simulation.tick-interval-ms:900}")
    private long tickIntervalMs = 900;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    @org.springframework.beans.factory.annotation.Qualifier("simulationScheduler")
    private java.util.concurrent.Executor asyncExecutor;

    public void setAsyncExecutor(java.util.concurrent.Executor asyncExecutor) {
        this.asyncExecutor = asyncExecutor;
    }

    public void setTickIntervalMs(long tickIntervalMs) {
        this.tickIntervalMs = tickIntervalMs;
    }

    public boolean isExecutionActive(Long startupId) {
        return activeExecutionWorkers.contains(startupId);
    }

    public synchronized void startExecutionWorker(Long startupId) {
        if (!activeExecutionWorkers.add(startupId)) {
            log.info("Execution worker already running for startup {}", startupId);
            return;
        }
        Runnable loopTask = () -> runAutonomousExecution(startupId);
        if (asyncExecutor != null) {
            asyncExecutor.execute(loopTask);
        } else {
            Thread worker = new Thread(loopTask, "exec-worker-" + startupId);
            worker.setDaemon(true);
            worker.start();
        }
    }

    private void runAutonomousExecution(Long startupId) {
        try {
            log.info("Autonomous execution loop started for startup {}", startupId);
            while (runningState.getOrDefault(startupId, false) && !pausedState.getOrDefault(startupId, false)) {
                ExecutionStateDto state = stepExecution(startupId);
                if (state.isCompleted()) {
                    log.info("Autonomous execution completed for startup {}", startupId);
                    break;
                }
                if (state.activeBlockerCount() > 0) {
                    List<ExecutionTask> tasks = taskRepository.findByStartupIdOrderById(startupId);
                    boolean hasWorkingTasks = tasks.stream()
                            .anyMatch(t -> t.getStatus() == TaskStatus.IN_PROGRESS);
                    if (!hasWorkingTasks) {
                        log.info("All active tasks blocked for startup {}; waiting for blocker resolution.", startupId);
                        break;
                    }
                }
                try {
                    Thread.sleep(Math.max(50L, tickIntervalMs));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Execution worker interrupted for startup {}", startupId);
                    break;
                }
            }
        } catch (Exception e) {
            log.error("Autonomous execution failed for startup {}", startupId, e);
        } finally {
            activeExecutionWorkers.remove(startupId);
        }
    }

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
        startup.setSimulationCompleted(false);
        startup.setExecutionDay(1);
        startup.setExecutionRunning(true);
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

        startExecutionWorker(startupId);

        return getExecutionState(startupId);
    }

    @Transactional
    public ExecutionStateDto pauseExecution(Long startupId) {
        pausedState.put(startupId, true);
        runningState.put(startupId, false);
        startupRepository.findById(startupId).ifPresent(s -> {
            s.setExecutionRunning(false);
            startupRepository.save(s);
        });
        executionEventService.emitExecutionPaused(startupId);
        return getExecutionState(startupId);
    }

    @Transactional
    public ExecutionStateDto resumeExecution(Long startupId) {
        pausedState.put(startupId, false);
        runningState.put(startupId, true);
        startupRepository.findById(startupId).ifPresent(s -> {
            s.setExecutionRunning(true);
            startupRepository.save(s);
        });
        executionEventService.emitExecutionResumed(startupId);
        startExecutionWorker(startupId);
        return getExecutionState(startupId);
    }

    @Transactional
    public ExecutionStateDto stepExecution(Long startupId) {
        // Restore from DB if the in-memory state was lost (e.g. after a restart).
        int day = currentDays.computeIfAbsent(startupId, id -> {
            return startupRepository.findById(id).map(Startup::getExecutionDay).orElse(1);
        }) + 1;
        currentDays.put(startupId, day);
        runningState.put(startupId, true);

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

        // 3. Calculate updated metrics
        Map<String, Object> metrics = metricsService.calculateMetrics(startupId, day);
        executionEventService.emitMetricChanged(startupId, metrics);

        // 4. Check execution completion
        boolean allCompleted = allTasks.stream().allMatch(t -> t.getStatus() == TaskStatus.COMPLETED);
        if (allCompleted && !allTasks.isEmpty()) {
            runningState.put(startupId, false);
            pausedState.put(startupId, false);
            Startup startup = startupRepository.findById(startupId).orElse(null);
            if (startup != null) {
                startup.setCurrentPhase(SimulationPhase.COMPLETED);
                startup.setSimulationCompleted(true);
                startup.setExecutionRunning(false);
                startup.setExecutionDay(day);
                startupRepository.save(startup);
            }
            double overall = (double) metrics.get("overallProgress");
            executionEventService.emitExecutionCompleted(startupId, overall);
        } else {
            // Persist the current day so restart can recover.
            startupRepository.findById(startupId).ifPresent(s -> {
                s.setExecutionDay(day);
                s.setExecutionRunning(true);
                startupRepository.save(s);
            });
        }

        return getExecutionState(startupId);
    }

    @Transactional
    public ExecutionStateDto resolveTaskBlocker(Long startupId, Long taskId) {
        ExecutionTask task = blockerService.resolveBlocker(startupId, taskId);
        updateAgentState(startupId, task.getDepartment(), AgentState.WORKING, "Resumed work on " + task.getTitle());
        executionEventService.emitTaskStarted(startupId, task);
        if (runningState.getOrDefault(startupId, true) && !pausedState.getOrDefault(startupId, false)) {
            startExecutionWorker(startupId);
        }
        return getExecutionState(startupId);
    }

    @Transactional(readOnly = true)
    public ExecutionStateDto getExecutionState(Long startupId) {
        Startup startup = startupRepository.findById(startupId)
                .orElseThrow(() -> new IllegalArgumentException("Startup not found: " + startupId));

        // Restore from DB when the in-memory map is absent (e.g. after a restart).
        int day = currentDays.computeIfAbsent(startupId, id -> startup.getExecutionDay());
        boolean isRunning = runningState.computeIfAbsent(startupId, id -> startup.isExecutionRunning());
        boolean isPaused = pausedState.getOrDefault(startupId, false);
        int week = ((day - 1) / 7) + 1;

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
