package com.startupsimulator.service;

import com.startupsimulator.model.Startup;
import com.startupsimulator.model.enums.EventType;
import com.startupsimulator.model.enums.SimulationPhase;
import com.startupsimulator.orchestrator.StartupOrchestrator;
import com.startupsimulator.repository.StartupEventRepository;
import com.startupsimulator.repository.StartupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Recovers interrupted startup executions automatically after backend restart.
 *
 * <p>Requirements:
 * 1. Identifies startups that were genuinely running when the backend stopped.
 * 2. Does not resume completed or failed startups.
 * 3. Prevents duplicate execution and concurrent workers for the same startup.
 * 4. Resumes safely through the existing orchestration path.
 */
@Slf4j
@Service
public class StartupExecutionRecoveryService {

    private final StartupRepository startupRepository;
    private final StartupOrchestrator orchestrator;
    private final ExecutionEngine executionEngine;
    private final StartupEventRepository eventRepository;

    public StartupExecutionRecoveryService(StartupRepository startupRepository,
                                           StartupOrchestrator orchestrator,
                                           ExecutionEngine executionEngine,
                                           StartupEventRepository eventRepository) {
        this.startupRepository = startupRepository;
        this.orchestrator = orchestrator;
        this.executionEngine = executionEngine;
        this.eventRepository = eventRepository;
    }

    @Value("${app.recovery.enabled:true}")
    private boolean recoveryEnabled = true;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!recoveryEnabled) {
            log.info("Automatic startup execution recovery is disabled via configuration.");
            return;
        }
        recoverInterruptedStartups();
    }

    /**
     * Scans all startups and re-queues genuinely interrupted ones.
     *
     * @return List of startup IDs that were recovered.
     */
    public List<Long> recoverInterruptedStartups() {
        List<Startup> allStartups = startupRepository.findAll();
        List<Long> recoveredIds = new ArrayList<>();

        for (Startup startup : allStartups) {
            Long id = startup.getId();
            if (id == null) continue;

            // 1. Exclude completed startups (either explicitly marked COMPLETED or planning-completed when not in execution)
            if (startup.getCurrentPhase() == SimulationPhase.COMPLETED ||
               (startup.getCurrentPhase() != SimulationPhase.EXECUTION && startup.isSimulationCompleted())) {
                log.debug("Startup {} is already completed; skipping recovery.", id);
                continue;
            }

            // 2. Exclude startups that have never been started (fresh ideas)
            if (!startup.isSimulationStarted()) {
                log.debug("Startup {} has not started simulation yet; skipping recovery.", id);
                continue;
            }

            // 3. Exclude failed or explicitly terminated startups
            if (isFailedStartup(id)) {
                log.info("Startup {} was terminated with failure; skipping recovery.", id);
                continue;
            }

            // 4. Case A: Phase 2F Execution
            if (startup.getCurrentPhase() == SimulationPhase.EXECUTION) {
                if (startup.isExecutionRunning() && !executionEngine.isExecutionActive(id)) {
                    log.info("Recovering interrupted Phase 2F execution state for startup {}", id);
                    executionEngine.resumeExecution(id);
                    recoveredIds.add(id);
                } else if (!startup.isExecutionRunning()) {
                    log.info("Startup {} is in EXECUTION phase but was explicitly paused; skipping recovery.", id);
                }
                continue;
            }

            // 5. Case B: Simulation Orchestration (ANALYSIS, DEBATE, DECISION, PLAN)
            if (!orchestrator.isSimulationActive(id)) {
                log.info("Recovering interrupted simulation orchestration for startup {} (phase={})",
                        id, startup.getCurrentPhase());
                orchestrator.resumeSimulation(id, 1.0);
                recoveredIds.add(id);
            }
        }

        log.info("Startup recovery scan complete. Recovered {} startup(s): {}", recoveredIds.size(), recoveredIds);
        return recoveredIds;
    }

    /**
     * Determines whether a startup terminated with failure.
     */
    public boolean isFailedStartup(Long startupId) {
        return eventRepository.findByStartupIdOrderById(startupId).stream()
                .anyMatch(e -> e.getType() == EventType.ORCHESTRATION_RUNTIME_TERMINATED);
    }
}

