package com.startupsimulator.service;

import com.startupsimulator.model.ExecutionTask;
import com.startupsimulator.model.enums.AgentType;
import com.startupsimulator.model.enums.TaskPriority;
import com.startupsimulator.model.enums.TaskStatus;
import com.startupsimulator.repository.ExecutionTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link StartupMetricsService#calculateMetrics} overall-progress,
 * which is an effort-weighted average: a task contributes to overall progress in
 * proportion to its {@code estimatedDays} (its share of total planned work), not
 * one equal slice per task. Repository and budget context are mocked so the test
 * exercises the pure metric math in isolation.
 */
class StartupMetricsServiceTest {

    private static final long STARTUP = 1L;

    private ExecutionTaskRepository taskRepository;
    private StartupContextService contextService;
    private StartupMetricsService metricsService;

    @BeforeEach
    void setUp() {
        taskRepository = mock(ExecutionTaskRepository.class);
        contextService = mock(StartupContextService.class);
        // budget() left returning null → financial defaults; these tests assert overall progress only.
        when(contextService.budget(anyLong())).thenReturn(null);
        metricsService = new StartupMetricsService(taskRepository, contextService);
    }

    private ExecutionTask task(AgentType dept, int estimatedDays, double progress, TaskStatus status) {
        ExecutionTask t = new ExecutionTask(STARTUP, dept, "agent", "title", "desc", TaskPriority.HIGH, estimatedDays);
        t.setProgress(progress);
        t.setStatus(status);
        return t;
    }

    private double overall(List<ExecutionTask> tasks) {
        when(taskRepository.findByStartupIdOrderById(STARTUP)).thenReturn(tasks);
        return (double) metricsService.calculateMetrics(STARTUP, 1).get("overallProgress");
    }

    @Test
    @DisplayName("Weighted by estimatedDays: a long task's progress counts more than a short task's")
    void weightedByEstimatedDays() {
        // Short task (2 days) fully done, long task (8 days) not started.
        // Unweighted mean would be 50%; effort-weighted is (1.0*2 + 0*8)/10 = 20%.
        double o = overall(List.of(
                task(AgentType.CEO, 2, 100.0, TaskStatus.COMPLETED),
                task(AgentType.DEVELOPMENT, 8, 0.0, TaskStatus.PENDING)));
        assertThat(o).isEqualTo(20.0);
    }

    @Test
    @DisplayName("Partially completed and completed tasks sum by work share")
    void partialAndCompletedTasks() {
        // (0.5*4 + 1.0*6) / (4+6) = 8/10 = 80%
        double o = overall(List.of(
                task(AgentType.DEVELOPMENT, 4, 50.0, TaskStatus.IN_PROGRESS),
                task(AgentType.MARKETING, 6, 100.0, TaskStatus.COMPLETED)));
        assertThat(o).isEqualTo(80.0);
    }

    @Test
    @DisplayName("Equal durations reduce to a simple mean")
    void equalDurationsAreSimpleMean() {
        // all estDays equal → weighting is uniform: (40+60+0)/3 = 33.33 → 33.3
        double o = overall(List.of(
                task(AgentType.CEO, 5, 40.0, TaskStatus.IN_PROGRESS),
                task(AgentType.DEVELOPMENT, 5, 60.0, TaskStatus.IN_PROGRESS),
                task(AgentType.FINANCE, 5, 0.0, TaskStatus.PENDING)));
        assertThat(o).isEqualTo(33.3);
    }

    @Test
    @DisplayName("Empty task list yields 0% overall progress (empty-task contract preserved)")
    void emptyTaskList() {
        when(taskRepository.findByStartupIdOrderById(STARTUP)).thenReturn(List.of());
        java.util.Map<String, Object> m = metricsService.calculateMetrics(STARTUP, 1);
        assertThat((double) m.get("overallProgress")).isEqualTo(0.0);
        assertThat((double) m.get("mvpProgress")).isEqualTo(0.0);
        // empty-task financial defaults from the existing contract
        assertThat((double) m.get("financialHealth")).isEqualTo(100.0);
    }

    @Test
    @DisplayName("Result is rounded to one decimal place")
    void roundingToOneDecimal() {
        // single 1-day task at 2.645% → (0.02645*1/1)*100 = 2.645 → rounds to 2.6
        double o = overall(List.of(task(AgentType.CEO, 1, 2.645, TaskStatus.IN_PROGRESS)));
        assertThat(o).isEqualTo(2.6);
    }

    @Test
    @DisplayName("estimatedDays <= 0 is floored to 1 so a task is never weightless")
    void nonPositiveEstimatedDaysFlooredToOne() {
        // estDays 0 → treated as 1. (1.0*1 + 0*1)/(1+1) = 50%
        double o = overall(List.of(
                task(AgentType.CEO, 0, 100.0, TaskStatus.COMPLETED),
                task(AgentType.DEVELOPMENT, 0, 0.0, TaskStatus.PENDING)));
        assertThat(o).isEqualTo(50.0);
    }

    @Test
    @DisplayName("All tasks complete clamps to 100%")
    void allCompleteClampsTo100() {
        double o = overall(List.of(
                task(AgentType.CEO, 3, 100.0, TaskStatus.COMPLETED),
                task(AgentType.DEVELOPMENT, 7, 100.0, TaskStatus.COMPLETED)));
        assertThat(o).isEqualTo(100.0, within(0.0001));
    }
}
