package com.startupsimulator.service;

import com.startupsimulator.model.Budget;
import com.startupsimulator.model.ExecutionTask;
import com.startupsimulator.model.enums.AgentType;
import com.startupsimulator.repository.ExecutionTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Calculates dynamic startup performance metrics, progress bars, budget burn, and runway
 * from current execution task states and budget parameters.
 */
@Service
@RequiredArgsConstructor
public class StartupMetricsService {

    private final ExecutionTaskRepository taskRepository;
    private final StartupContextService contextService;

    public Map<String, Object> calculateMetrics(Long startupId, int currentDay) {
        List<ExecutionTask> tasks = taskRepository.findByStartupIdOrderById(startupId);

        if (tasks.isEmpty()) {
            return Map.of(
                    "overallProgress", 0.0,
                    "mvpProgress", 0.0,
                    "technicalProgress", 0.0,
                    "marketReadiness", 0.0,
                    "financialHealth", 100.0,
                    "budgetRemaining", 100000.0,
                    "runwayMonths", 12.0
            );
        }

        // 1. Overall Progress (effort-weighted by estimatedDays, so a task's
        // contribution is proportional to its share of total planned work rather
        // than counting every task equally).
        double totalWork = tasks.stream()
                .mapToDouble(t -> Math.max(1, t.getEstimatedDays()))
                .sum();
        double doneWork = tasks.stream()
                .mapToDouble(t -> (t.getProgress() / 100.0) * Math.max(1, t.getEstimatedDays()))
                .sum();
        double overallProgress = totalWork == 0.0
                ? 0.0
                : Math.min(100.0, Math.max(0.0, (doneWork / totalWork) * 100.0));

        // 2. Department Specific Progress Metrics
        double devProgress = calcDepartmentProgress(tasks, AgentType.DEVELOPMENT);
        double mktProgress = calcDepartmentProgress(tasks, AgentType.MARKETING);
        double finProgress = calcDepartmentProgress(tasks, AgentType.FINANCE);
        double ceoProgress = calcDepartmentProgress(tasks, AgentType.CEO);

        double mvpProgress = devProgress;
        double technicalProgress = devProgress;
        double marketReadiness = mktProgress;

        // 3. Financial Health & Runway Calculations
        Budget budget = contextService.budget(startupId);
        double startingCapital = budget != null && budget.getStartingCapital() > 0
                ? budget.getStartingCapital()
                : 150000.0;
        double monthlyBurn = budget != null && budget.getMonthlyBurn() > 0
                ? budget.getMonthlyBurn()
                : 12000.0;

        double dailyBurn = monthlyBurn / 30.0;
        double burnedCapital = currentDay * dailyBurn;
        double budgetRemaining = Math.max(0.0, startingCapital - burnedCapital);
        double runwayMonths = monthlyBurn > 0 ? (budgetRemaining / monthlyBurn) : 0.0;
        double financialHealth = Math.min(100.0, Math.max(0.0, (budgetRemaining / startingCapital) * 100.0));

        Map<String, Double> departmentProgressMap = Map.of(
                "CEO", ceoProgress,
                "DEVELOPMENT", devProgress,
                "MARKETING", mktProgress,
                "FINANCE", finProgress
        );

        Map<String, Object> result = new HashMap<>();
        result.put("overallProgress", Math.round(overallProgress * 10.0) / 10.0);
        result.put("mvpProgress", Math.round(mvpProgress * 10.0) / 10.0);
        result.put("technicalProgress", Math.round(technicalProgress * 10.0) / 10.0);
        result.put("marketReadiness", Math.round(marketReadiness * 10.0) / 10.0);
        result.put("financialHealth", Math.round(financialHealth * 10.0) / 10.0);
        result.put("budgetRemaining", Math.round(budgetRemaining * 100.0) / 100.0);
        result.put("runwayMonths", Math.round(runwayMonths * 10.0) / 10.0);
        result.put("departmentProgress", departmentProgressMap);

        return result;
    }

    private double calcDepartmentProgress(List<ExecutionTask> tasks, AgentType dept) {
        List<ExecutionTask> deptTasks = tasks.stream()
                .filter(t -> t.getDepartment() == dept)
                .toList();
        if (deptTasks.isEmpty()) return 0.0;
        double sum = deptTasks.stream().mapToDouble(ExecutionTask::getProgress).sum();
        return sum / deptTasks.size();
    }
}
