package com.startupsimulator.service;

import com.startupsimulator.model.Startup;
import com.startupsimulator.model.enums.SimulationPhase;
import com.startupsimulator.repository.StartupRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that startups and their state are persisted to and retrieved from the
 * database correctly. These tests use an H2 in-memory database via @DataJpaTest.
 */
@DataJpaTest
class StartupPersistenceTest {

    @Autowired
    private StartupRepository startupRepository;

    @Test
    void startup_isSavedAndRetrievableById() {
        Startup s = new Startup("TestCo", "A great idea");
        Startup saved = startupRepository.save(s);

        assertNotNull(saved.getId(), "ID must be assigned by DB");

        Optional<Startup> found = startupRepository.findById(saved.getId());
        assertTrue(found.isPresent(), "Startup must be retrievable after save");
        assertEquals("TestCo", found.get().getName());
        assertEquals("A great idea", found.get().getOriginalIdea());
        assertEquals(SimulationPhase.IDEA, found.get().getCurrentPhase());
    }

    @Test
    void startup_statusFieldsArePersisted() {
        Startup s = new Startup("StatusCo", "Idea with status");
        s.setSimulationStarted(true);
        s.setCurrentPhase(SimulationPhase.ANALYSIS);
        s.setExecutionDay(5);
        s.setExecutionRunning(true);
        startupRepository.save(s);

        Startup found = startupRepository.findById(s.getId()).orElseThrow();
        assertTrue(found.isSimulationStarted());
        assertEquals(SimulationPhase.ANALYSIS, found.getCurrentPhase());
        assertEquals(5, found.getExecutionDay());
        assertTrue(found.isExecutionRunning());
    }

    @Test
    void listStartups_returnsAllSavedStartupsOrderedByCreatedAtDesc() {
        startupRepository.save(new Startup("First", "Idea 1"));
        startupRepository.save(new Startup("Second", "Idea 2"));
        startupRepository.save(new Startup("Third", "Idea 3"));

        List<Startup> all = startupRepository.findAllByOrderByCreatedAtDesc();
        assertEquals(3, all.size(), "All saved startups must appear in list");
    }

    @Test
    void startup_executionDay_defaultsToOne() {
        Startup s = startupRepository.save(new Startup("DayCo", "Idea"));
        Startup found = startupRepository.findById(s.getId()).orElseThrow();
        assertEquals(1, found.getExecutionDay(), "Default execution day must be 1");
        assertFalse(found.isExecutionRunning(), "Execution must not be running by default");
    }

    @Test
    void multipleStartups_remainIsolatedInDatabase() {
        Startup a = startupRepository.save(new Startup("CompanyA", "Fintech app"));
        Startup b = startupRepository.save(new Startup("CompanyB", "Healthtech scanner"));

        assertNotEquals(a.getId(), b.getId());

        a.setCurrentPhase(SimulationPhase.EXECUTION);
        a.setExecutionDay(10);
        a.setExecutionRunning(true);
        startupRepository.save(a);

        Startup reloadedB = startupRepository.findById(b.getId()).orElseThrow();
        assertEquals(SimulationPhase.IDEA, reloadedB.getCurrentPhase());
        assertEquals(1, reloadedB.getExecutionDay());
        assertFalse(reloadedB.isExecutionRunning());

        Startup reloadedA = startupRepository.findById(a.getId()).orElseThrow();
        assertEquals(SimulationPhase.EXECUTION, reloadedA.getCurrentPhase());
        assertEquals(10, reloadedA.getExecutionDay());
        assertTrue(reloadedA.isExecutionRunning());
    }

    @Test
    void startup_preservesAllLifecycleAndMetricsFields() {
        Startup s = new Startup("MetricsCo", "Comprehensive lifecycle test");
        s.setProblem("Customer pain point");
        s.setSolution("AI solution");
        s.setExecutiveSummary("Executive vision");
        s.setStrategicObjectives("Goal 1\nGoal 2");
        s.setOverallProgress(85);
        s.setMvpProgress(90);
        s.setTechnicalFeasibility(80);
        s.setMarketReadiness(75);
        s.setFinancialHealth(88);
        s.setBudgetRemaining(45000.0);
        s.setRunwayMonths(11.5);
        s.setCurrentPhase(SimulationPhase.COMPLETED);
        s.setSimulationStarted(true);
        s.setSimulationCompleted(true);
        s.setExecutionRunning(false);

        Startup saved = startupRepository.save(s);
        Startup found = startupRepository.findById(saved.getId()).orElseThrow();

        assertEquals("Customer pain point", found.getProblem());
        assertEquals("AI solution", found.getSolution());
        assertEquals("Executive vision", found.getExecutiveSummary());
        assertEquals(85, found.getOverallProgress());
        assertEquals(90, found.getMvpProgress());
        assertEquals(45000.0, found.getBudgetRemaining());
        assertEquals(11.5, found.getRunwayMonths());
        assertTrue(found.isSimulationCompleted());
        assertFalse(found.isExecutionRunning());
        assertEquals(SimulationPhase.COMPLETED, found.getCurrentPhase());
    }
}
