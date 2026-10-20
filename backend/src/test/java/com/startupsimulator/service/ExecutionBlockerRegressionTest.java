package com.startupsimulator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.startupsimulator.model.ExecutionTask;
import com.startupsimulator.model.Startup;
import com.startupsimulator.model.enums.AgentType;
import com.startupsimulator.model.enums.SimulationPhase;
import com.startupsimulator.model.enums.TaskPriority;
import com.startupsimulator.model.enums.TaskStatus;
import com.startupsimulator.repository.AgentRepository;
import com.startupsimulator.repository.ExecutionTaskRepository;
import com.startupsimulator.repository.StartupRepository;
import com.startupsimulator.trace.ExecutionTraceRepository;
import com.startupsimulator.trace.ExecutionTraceService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real H2 repositories and scheduler; no provider calls or background workers. */
@DataJpaTest
class ExecutionBlockerRegressionTest {
    @Autowired private ExecutionTaskRepository tasks;
    @Autowired private StartupRepository startups;
    @Autowired private AgentRepository agents;
    @Autowired private ExecutionTraceRepository traces;
    @Autowired private EntityManager entityManager;
    private ExecutionEngine engine;
    private ExecutionTraceService traceService;
    private ExecutionEventService events;
    private Startup startup;

    @BeforeEach
    void setUp() {
        traceService = new ExecutionTraceService(traces, new ObjectMapper());
        events = mock(ExecutionEventService.class);
        StartupMetricsService metrics = mock(StartupMetricsService.class);
        when(metrics.calculateMetrics(anyLong(), anyInt())).thenReturn(Map.of(
                "overallProgress", 0.0, "mvpProgress", 0.0, "technicalProgress", 0.0,
                "marketReadiness", 0.0, "financialHealth", 100.0, "budgetRemaining", 60000.0,
                "runwayMonths", 15.0, "departmentProgress", Map.of()));
        engine = new ExecutionEngine(startups, agents, tasks, mock(BlueprintService.class), mock(TaskGenerator.class),
                new TaskScheduler(tasks, traceService), metrics, new BlockerService(tasks, traceService), events, traceService);
        engine.setAsyncExecutor(command -> {});
        startup = new Startup("NPWR regression", "Test only");
        startup.setCurrentPhase(SimulationPhase.EXECUTION);
        startup = startups.save(startup);
    }

    private ExecutionTask task(String title, AgentType department, TaskStatus status) {
        ExecutionTask task = new ExecutionTask(startup.getId(), department, "Test owner", title, "Test", TaskPriority.HIGH, 6);
        task.setStatus(status);
        return tasks.save(task);
    }

    @ParameterizedTest
    @ValueSource(ints = {8, 18})
    void completedDependencyDoesNotInjectCalendarRiskAndRealBlockerRemains(int day) {
        startup.setExecutionDay(day - 1);
        // Reproduces the live Day 8 ordering: setup completes, API work is
        // scheduled, then the old calendar trigger used to block it immediately.
        ExecutionTask setup = task("Repo & Environment Architecture Setup", AgentType.DEVELOPMENT, TaskStatus.IN_PROGRESS);
        setup.setProgress(99.0);
        ExecutionTask api = task("Core Database & API Infrastructure", AgentType.DEVELOPMENT, TaskStatus.PENDING);
        api.setDependencies(List.of(setup.getId()));
        ExecutionTask downstream = task("Build MVP", AgentType.DEVELOPMENT, TaskStatus.PENDING);
        downstream.setDependencies(List.of(api.getId()));
        ExecutionTask realBlocker = task("Launch approval", AgentType.MARKETING, TaskStatus.BLOCKED);
        realBlocker.setBlockerReason("Legal approval of skincare claims required");

        var state = engine.stepExecution(startup.getId());
        entityManager.flush();
        entityManager.clear();
        assertEquals(day, state.day());
        assertEquals(TaskStatus.COMPLETED, tasks.findById(setup.getId()).orElseThrow().getStatus());
        ExecutionTask persistedApi = tasks.findById(api.getId()).orElseThrow();
        assertEquals(TaskStatus.IN_PROGRESS, persistedApi.getStatus());
        assertNull(persistedApi.getBlockerReason());
        assertEquals(1.0, persistedApi.getProgress());
        assertEquals(0, persistedApi.getActualDays());
        assertEquals(TaskStatus.PENDING, tasks.findById(downstream.getId()).orElseThrow().getStatus());
        ExecutionTask persistedReal = tasks.findById(realBlocker.getId()).orElseThrow();
        assertEquals(TaskStatus.BLOCKED, persistedReal.getStatus());
        assertEquals("Legal approval of skincare claims required", persistedReal.getBlockerReason());
        assertEquals(1, state.activeBlockerCount());
        verify(events, never()).emitTaskBlocked(anyLong(), any());
    }

    @Test
    void legacyResolutionPersistsOriginalBlockerAndResolutionWithoutCompletingWork() {
        startup.setExecutionDay(13);
        ExecutionTask setup = task("Repo & Environment Architecture Setup", AgentType.DEVELOPMENT, TaskStatus.COMPLETED);
        ExecutionTask api = task("Core Database & API Infrastructure", AgentType.DEVELOPMENT, TaskStatus.BLOCKED);
        api.setDependencies(List.of(setup.getId()));
        api.setProgress(1.0);
        String original = "DEVELOPMENT execution blocked on Day 8: Dependencies delayed due to technical risk review.";
        api.setBlockerReason(original);
        traceService.recordTaskStateChange(startup.getId(), null, "DEVELOPMENT", api.getId(), api.getTitle(),
                TaskStatus.IN_PROGRESS, TaskStatus.BLOCKED, original);
        Long startupId = startup.getId();
        Long apiId = api.getId();
        engine.resolveTaskBlocker(startupId, apiId);
        entityManager.flush();
        entityManager.clear();

        ExecutionTask persisted = tasks.findById(apiId).orElseThrow();
        assertEquals(TaskStatus.IN_PROGRESS, persisted.getStatus());
        assertEquals(1.0, persisted.getProgress());
        assertEquals(0, persisted.getActualDays());
        assertNull(persisted.getBlockerReason());
        var history = new ExecutionTraceService(traces, new ObjectMapper()).getTrace(startupId);
        assertEquals(2, history.size());
        assertEquals(original, history.get(0).reason());
        assertTrue(history.get(1).reason().contains(original));
        assertTrue(history.get(1).reason().contains("Validated completed dependencies [" + setup.getId() + "]"));
        assertEquals("IN_PROGRESS", history.get(1).outcome());
        assertTrue(history.get(1).sequenceNumber() > history.get(0).sequenceNumber());
        verify(events, never()).emitTaskCompleted(anyLong(), any());
        // Only the normal engine advances the resumed work on the next day.
        engine.stepExecution(startupId);
        assertTrue(tasks.findById(apiId).orElseThrow().getProgress() > 1.0);
        assertEquals(1, tasks.findById(apiId).orElseThrow().getActualDays());
    }

    @Test
    void unresolvedPrerequisitePreventsResolutionAndFalseStartEvent() {
        ExecutionTask setup = task("Repo setup", AgentType.DEVELOPMENT, TaskStatus.PENDING);
        ExecutionTask api = task("Core API", AgentType.DEVELOPMENT, TaskStatus.BLOCKED);
        api.setDependencies(List.of(setup.getId()));
        api.setBlockerReason("Waiting for repo setup");
        assertThrows(IllegalStateException.class, () -> engine.resolveTaskBlocker(startup.getId(), api.getId()));
        assertEquals(TaskStatus.BLOCKED, api.getStatus());
        verify(events, never()).emitTaskStarted(anyLong(), any());
        assertTrue(traceService.getTrace(startup.getId()).isEmpty());
    }
}
