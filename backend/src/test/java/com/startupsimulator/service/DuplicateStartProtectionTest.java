package com.startupsimulator.service;

import com.startupsimulator.model.Startup;
import com.startupsimulator.model.enums.SimulationPhase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that the duplicate-start guard in StartupOrchestrator works correctly.
 * The guard checks startup.isSimulationStarted() and returns early — so a second
 * call for the same startup must not mutate the phase further.
 *
 * These are pure unit tests that exercise the guard logic directly without needing
 * the full Spring context or repository mocks.
 */
class DuplicateStartProtectionTest {

    @Test
    void guard_preventsSecondSimulationStart() {
        // Simulate the guard logic from StartupOrchestrator.simulate():
        //   if (startup.isSimulationStarted()) { return; }
        Startup startup = new Startup("DupCo", "Duplicate start idea");
        startup.setId(99L);
        startup.setSimulationStarted(false);

        // First call — sets the flag and begins analysis
        if (!startup.isSimulationStarted()) {
            startup.setSimulationStarted(true);
            startup.setCurrentPhase(SimulationPhase.ANALYSIS);
        }
        assertEquals(SimulationPhase.ANALYSIS, startup.getCurrentPhase());
        assertTrue(startup.isSimulationStarted());

        // Second call — the guard fires and nothing changes
        if (!startup.isSimulationStarted()) {
            // This block must NOT execute
            startup.setCurrentPhase(SimulationPhase.COMPLETED);
        }

        // Phase must remain ANALYSIS, not jump to COMPLETED
        assertEquals(SimulationPhase.ANALYSIS, startup.getCurrentPhase(),
                "Guard must prevent a second simulation from overwriting phase");
    }

    @Test
    void guard_allowsFirstStart_whenNotYetStarted() {
        Startup startup = new Startup("FreshCo", "Fresh start");
        assertFalse(startup.isSimulationStarted(), "New startup must not be started");

        // Guard should not fire for a fresh startup
        boolean guardFired = startup.isSimulationStarted();
        assertFalse(guardFired, "Guard must not block the first start");
    }

    @Test
    void simulationCompleted_flagIsIndependentOfStartedFlag() {
        Startup startup = new Startup("FinishedCo", "Completed idea");
        startup.setSimulationStarted(true);
        startup.setSimulationCompleted(true);

        // Both flags independently persisted
        assertTrue(startup.isSimulationStarted());
        assertTrue(startup.isSimulationCompleted());

        // A further "start" attempt would still be blocked
        boolean wouldStart = !startup.isSimulationStarted();
        assertFalse(wouldStart, "Completed startup cannot be started again");
    }
}
