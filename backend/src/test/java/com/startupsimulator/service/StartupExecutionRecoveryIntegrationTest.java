package com.startupsimulator.service;

import com.startupsimulator.model.Startup;
import com.startupsimulator.model.StartupEvent;
import com.startupsimulator.model.enums.EventType;
import com.startupsimulator.model.enums.SimulationPhase;
import com.startupsimulator.orchestrator.StartupOrchestrator;
import com.startupsimulator.repository.StartupEventRepository;
import com.startupsimulator.repository.StartupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration and unit tests verifying automatic startup recovery,
 * completed-startup exclusion, failed-startup exclusion, and duplicate-start prevention.
 */
@ExtendWith(MockitoExtension.class)
class StartupExecutionRecoveryIntegrationTest {

    @Mock private StartupRepository startupRepository;
    @Mock private StartupOrchestrator orchestrator;
    @Mock private ExecutionEngine executionEngine;
    @Mock private StartupEventRepository eventRepository;

    private StartupExecutionRecoveryService recoveryService;

    @BeforeEach
    void setUp() {
        recoveryService = new StartupExecutionRecoveryService(
                startupRepository, orchestrator, executionEngine, eventRepository
        );
    }

    @Test
    void recoverInterruptedStartups_resumesInterruptedAnalysisSimulation() {
        Startup startup = new Startup("InterruptedCo", "Running when backend stopped");
        startup.setId(101L);
        startup.setSimulationStarted(true);
        startup.setSimulationCompleted(false);
        startup.setCurrentPhase(SimulationPhase.ANALYSIS);

        when(startupRepository.findAll()).thenReturn(List.of(startup));
        when(eventRepository.findByStartupIdOrderById(101L)).thenReturn(List.of());
        when(orchestrator.isSimulationActive(101L)).thenReturn(false);

        List<Long> recovered = recoveryService.recoverInterruptedStartups();

        assertEquals(List.of(101L), recovered);
        verify(orchestrator).resumeSimulation(101L, 1.0);
        verify(executionEngine, never()).getExecutionState(anyLong());
    }

    @Test
    void recoverInterruptedStartups_excludesCompletedStartups() {
        Startup completed1 = new Startup("DoneCo1", "Finished via flag");
        completed1.setId(201L);
        completed1.setSimulationStarted(true);
        completed1.setSimulationCompleted(true);
        completed1.setCurrentPhase(SimulationPhase.COMPLETED);

        Startup completed2 = new Startup("DoneCo2", "Finished via phase");
        completed2.setId(202L);
        completed2.setSimulationStarted(true);
        completed2.setSimulationCompleted(false);
        completed2.setCurrentPhase(SimulationPhase.COMPLETED);

        when(startupRepository.findAll()).thenReturn(List.of(completed1, completed2));

        List<Long> recovered = recoveryService.recoverInterruptedStartups();

        assertTrue(recovered.isEmpty(), "Completed startups must not be recovered");
        verify(orchestrator, never()).resumeSimulation(anyLong(), anyDouble());
        verify(executionEngine, never()).getExecutionState(anyLong());
    }

    @Test
    void recoverInterruptedStartups_excludesFreshUnstartedStartups() {
        Startup fresh = new Startup("NewIdeaCo", "Never started");
        fresh.setId(301L);
        fresh.setSimulationStarted(false);
        fresh.setSimulationCompleted(false);
        fresh.setCurrentPhase(SimulationPhase.IDEA);

        when(startupRepository.findAll()).thenReturn(List.of(fresh));

        List<Long> recovered = recoveryService.recoverInterruptedStartups();

        assertTrue(recovered.isEmpty(), "Unstarted startups must not be recovered");
        verify(orchestrator, never()).resumeSimulation(anyLong(), anyDouble());
    }

    @Test
    void recoverInterruptedStartups_excludesTerminatedFailedStartups() {
        Startup failed = new Startup("FailedCo", "Explicitly terminated");
        failed.setId(401L);
        failed.setSimulationStarted(true);
        failed.setSimulationCompleted(false);
        failed.setCurrentPhase(SimulationPhase.ANALYSIS);

        StartupEvent termEvent = new StartupEvent(401L, EventType.ORCHESTRATION_RUNTIME_TERMINATED, "Terminated", null);

        when(startupRepository.findAll()).thenReturn(List.of(failed));
        when(eventRepository.findByStartupIdOrderById(401L)).thenReturn(List.of(termEvent));

        List<Long> recovered = recoveryService.recoverInterruptedStartups();

        assertTrue(recovered.isEmpty(), "Terminated/failed startups must not be recovered");
        verify(orchestrator, never()).resumeSimulation(anyLong(), anyDouble());
    }

    @Test
    void recoverInterruptedStartups_resumesRunningPhase2FExecution() {
        Startup execRunning = new Startup("ExecCo", "Phase 2F execution was active");
        execRunning.setId(501L);
        execRunning.setSimulationStarted(true);
        execRunning.setSimulationCompleted(false);
        execRunning.setCurrentPhase(SimulationPhase.EXECUTION);
        execRunning.setExecutionRunning(true);
        execRunning.setExecutionDay(4);

        Startup execPaused = new Startup("PausedExecCo", "Phase 2F execution was paused");
        execPaused.setId(502L);
        execPaused.setSimulationStarted(true);
        execPaused.setSimulationCompleted(false);
        execPaused.setCurrentPhase(SimulationPhase.EXECUTION);
        execPaused.setExecutionRunning(false); // Paused before restart
        execPaused.setExecutionDay(2);

        when(startupRepository.findAll()).thenReturn(List.of(execRunning, execPaused));

        List<Long> recovered = recoveryService.recoverInterruptedStartups();

        assertEquals(List.of(501L), recovered, "Only actively running Phase 2F startup must be recovered");
        verify(executionEngine).resumeExecution(501L);
        verify(executionEngine, never()).resumeExecution(502L);
        verify(orchestrator, never()).resumeSimulation(anyLong(), anyDouble());
    }

    @Test
    void duplicateStartPrevention_preventsConcurrentWorkersForSameStartup() {
        Startup startup = new Startup("ActiveCo", "Already running worker");
        startup.setId(601L);
        startup.setSimulationStarted(true);
        startup.setSimulationCompleted(false);
        startup.setCurrentPhase(SimulationPhase.ANALYSIS);

        when(startupRepository.findAll()).thenReturn(List.of(startup));
        when(eventRepository.findByStartupIdOrderById(601L)).thenReturn(List.of());
        // Worker is ALREADY active for 601
        when(orchestrator.isSimulationActive(601L)).thenReturn(true);

        List<Long> recovered = recoveryService.recoverInterruptedStartups();

        assertTrue(recovered.isEmpty(), "Must skip startup if worker is already active");
        verify(orchestrator, never()).resumeSimulation(eq(601L), anyDouble());
    }
}
