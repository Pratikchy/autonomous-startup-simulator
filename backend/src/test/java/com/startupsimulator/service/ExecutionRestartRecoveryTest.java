package com.startupsimulator.service;

import com.startupsimulator.dto.response.ExecutionStateDto;
import com.startupsimulator.model.Startup;
import com.startupsimulator.model.enums.AgentType;
import com.startupsimulator.model.enums.SimulationPhase;
import com.startupsimulator.model.enums.TaskPriority;
import com.startupsimulator.model.enums.TaskStatus;
import com.startupsimulator.model.ExecutionTask;
import com.startupsimulator.repository.AgentRepository;
import com.startupsimulator.repository.ExecutionTaskRepository;
import com.startupsimulator.repository.StartupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Verifies that ExecutionEngine restores execution state from the database
 * when its in-memory maps are empty (simulating a backend restart scenario).
 */
@ExtendWith(MockitoExtension.class)
class ExecutionRestartRecoveryTest {

    @Mock private StartupRepository startupRepository;
    @Mock private AgentRepository agentRepository;
    @Mock private ExecutionTaskRepository taskRepository;
    @Mock private BlueprintService blueprintService;
    @Mock private TaskGenerator taskGenerator;
    @Mock private TaskScheduler taskScheduler;
    @Mock private StartupMetricsService metricsService;
    @Mock private BlockerService blockerService;
    @Mock private ExecutionEventService executionEventService;

    private ExecutionEngine executionEngine;

    @BeforeEach
    void setUp() {
        executionEngine = new ExecutionEngine(
                startupRepository, agentRepository, taskRepository,
                blueprintService, taskGenerator, taskScheduler,
                metricsService, blockerService, executionEventService
        );
    }

    @Test
    void getExecutionState_restoresDayFromDb_whenInMemoryMapIsEmpty() {
        // Simulate a startup that was on day 7 before the restart
        Startup startup = new Startup("RecoverCo", "Post-restart idea");
        startup.setId(42L);
        startup.setCurrentPhase(SimulationPhase.EXECUTION);
        startup.setExecutionDay(7);         // Persisted before restart
        startup.setExecutionRunning(true);  // Persisted before restart

        when(startupRepository.findById(42L)).thenReturn(Optional.of(startup));

        ExecutionTask task = new ExecutionTask(42L, AgentType.DEVELOPMENT, "Dev Lead",
                "Build MVP", "Desc", TaskPriority.HIGH, 10);
        task.setId(1L);
        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setProgress(70.0);
        when(taskRepository.findByStartupIdOrderById(42L)).thenReturn(List.of(task));
        when(metricsService.calculateMetrics(eq(42L), anyInt())).thenReturn(Map.of(
                "overallProgress", 70.0, "mvpProgress", 50.0,
                "technicalProgress", 70.0, "marketReadiness", 30.0,
                "financialHealth", 80.0, "budgetRemaining", 120000.0,
                "runwayMonths", 10.0,
                "departmentProgress", Map.of("DEVELOPMENT", 70.0)
        ));

        // In-memory maps are EMPTY here (simulating fresh JVM / restart)
        ExecutionStateDto state = executionEngine.getExecutionState(42L);

        assertNotNull(state);
        // Day must be restored from DB (7), not default 1
        assertEquals(7, state.day(), "Day must be restored from DB-persisted executionDay after restart");
        // Running state must be restored from DB
        assertTrue(state.isRunning(), "isRunning must be restored from DB-persisted executionRunning");
        // Week computed correctly from restored day
        assertEquals(1, state.week(), "Week 1 for days 1-7");
    }

    @Test
    void getExecutionState_showsNotRunning_whenDbSaysNotRunning() {
        Startup startup = new Startup("PausedCo", "Was paused");
        startup.setId(55L);
        startup.setCurrentPhase(SimulationPhase.EXECUTION);
        startup.setExecutionDay(3);
        startup.setExecutionRunning(false); // Paused before restart

        when(startupRepository.findById(55L)).thenReturn(Optional.of(startup));
        when(taskRepository.findByStartupIdOrderById(55L)).thenReturn(List.of());
        when(metricsService.calculateMetrics(eq(55L), anyInt())).thenReturn(Map.of(
                "overallProgress", 0.0, "mvpProgress", 0.0,
                "technicalProgress", 0.0, "marketReadiness", 0.0,
                "financialHealth", 100.0, "budgetRemaining", 150000.0,
                "runwayMonths", 12.0, "departmentProgress", Map.of()
        ));

        ExecutionStateDto state = executionEngine.getExecutionState(55L);

        assertFalse(state.isRunning(), "Not running state must be restored from DB after restart");
        assertEquals(3, state.day(), "Day must be restored as 3");
    }
}
