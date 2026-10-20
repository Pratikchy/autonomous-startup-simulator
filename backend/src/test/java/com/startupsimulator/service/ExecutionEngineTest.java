package com.startupsimulator.service;

import com.startupsimulator.dto.response.BlueprintDto;
import com.startupsimulator.dto.response.ExecutionStateDto;
import com.startupsimulator.model.ExecutionTask;
import com.startupsimulator.model.Startup;
import com.startupsimulator.model.enums.AgentType;
import com.startupsimulator.model.enums.SimulationPhase;
import com.startupsimulator.model.enums.TaskPriority;
import com.startupsimulator.model.enums.TaskStatus;
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

@ExtendWith(MockitoExtension.class)
class ExecutionEngineTest {

    @Mock
    private StartupRepository startupRepository;
    @Mock
    private AgentRepository agentRepository;
    @Mock
    private ExecutionTaskRepository taskRepository;
    @Mock
    private BlueprintService blueprintService;
    @Mock
    private TaskGenerator taskGenerator;
    @Mock
    private TaskScheduler taskScheduler;
    @Mock
    private StartupMetricsService metricsService;
    @Mock
    private BlockerService blockerService;
    @Mock
    private ExecutionEventService executionEventService;

    private ExecutionEngine executionEngine;

    @BeforeEach
    void setUp() {
        executionEngine = new ExecutionEngine(
                startupRepository,
                agentRepository,
                taskRepository,
                blueprintService,
                taskGenerator,
                taskScheduler,
                metricsService,
                blockerService,
                executionEventService
        );
    }

    @Test
    void testStartExecution() {
        Startup startup = new Startup("Nova AI", "AI Platform");
        startup.setId(1L);
        startup.setCurrentPhase(SimulationPhase.PLAN);

        when(startupRepository.findById(1L)).thenReturn(Optional.of(startup));
        when(blueprintService.build(1L)).thenReturn(BlueprintDto.builder().startupId(1L).name("Nova AI").build());

        ExecutionTask task = new ExecutionTask(1L, AgentType.CEO, "CEO", "Kickoff", "Desc", TaskPriority.HIGH, 3);
        task.setId(10L);

        when(taskRepository.findByStartupIdOrderById(1L)).thenReturn(List.of(task));
        when(taskScheduler.scheduleAvailableTasks(1L)).thenReturn(List.of(task));
        when(metricsService.calculateMetrics(eq(1L), anyInt())).thenReturn(Map.of(
                "overallProgress", 10.0,
                "mvpProgress", 0.0,
                "technicalProgress", 0.0,
                "marketReadiness", 0.0,
                "financialHealth", 100.0,
                "budgetRemaining", 150000.0,
                "runwayMonths", 12.0,
                "departmentProgress", Map.of("CEO", 10.0, "DEVELOPMENT", 0.0, "MARKETING", 0.0, "FINANCE", 0.0)
        ));

        executionEngine.setAsyncExecutor(command -> {}); // Do not leak a real worker from this unit test.
        ExecutionStateDto state = executionEngine.startExecution(1L);

        assertNotNull(state);
        assertTrue(state.isRunning());
        assertEquals(SimulationPhase.EXECUTION, startup.getCurrentPhase());
        verify(executionEventService).emitExecutionStarted(eq(1L), anyInt());
    }

    @Test
    void testStepExecution() {
        Startup startup = new Startup("Nova AI", "AI Platform");
        startup.setId(1L);
        startup.setCurrentPhase(SimulationPhase.EXECUTION);

        when(startupRepository.findById(1L)).thenReturn(Optional.of(startup));

        ExecutionTask task = new ExecutionTask(1L, AgentType.DEVELOPMENT, "Dev Lead", "MVP", "Desc", TaskPriority.HIGH, 2);
        task.setId(20L);
        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setProgress(50.0);

        when(taskRepository.findByStartupIdOrderById(1L)).thenReturn(List.of(task));
        when(metricsService.calculateMetrics(eq(1L), anyInt())).thenReturn(Map.of(
                "overallProgress", 100.0,
                "mvpProgress", 100.0,
                "technicalProgress", 100.0,
                "marketReadiness", 100.0,
                "financialHealth", 100.0,
                "budgetRemaining", 140000.0,
                "runwayMonths", 11.0,
                "departmentProgress", Map.of("DEVELOPMENT", 100.0)
        ));

        ExecutionStateDto state = executionEngine.stepExecution(1L);

        assertNotNull(state);
        assertEquals(2, state.day());
        assertEquals(100.0, task.getProgress()); // Advance 50% + 50% = 100%
        assertEquals(TaskStatus.COMPLETED, task.getStatus());
        verify(executionEventService).emitTaskCompleted(eq(1L), eq(task));
    }
}
