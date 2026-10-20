import { describe, it, expect } from "vitest";
import {
  isExecutionLifecycle,
  getPlanningTaskStats,
  getExecutionTaskStats,
} from "./lifecycle";
import type { AgentTask, ExecutionState, ExecutionTask, Startup } from "../types";

describe("lifecycle detection", () => {
  const baseStartup: Startup = {
    id: 1,
    name: "AI Copilot",
    originalIdea: "An AI agent for coding",
    currentPhase: "ANALYSIS",
    problem: null,
    solution: null,
    targetAudience: null,
    valueProposition: null,
    businessModel: null,
    constraints: null,
    executiveSummary: null,
    strategicObjectives: [],
    assumptions: [],
    mvpDirection: null,
    ceoAnalysisProvider: null,
    ceoAnalysisFailed: false,
    ceoAnalysisError: null,
    health: {
      overallProgress: 30,
      mvpProgress: 0,
      technicalFeasibility: 50,
      marketReadiness: 50,
      financialHealth: 50,
      budgetRemaining: 100000,
      runwayMonths: 12,
    },
    simulationStarted: true,
    simulationCompleted: false,
    createdAt: "2026-10-18T00:00:00Z",
    updatedAt: "2026-10-18T00:00:00Z",
  };

  const emptyExecutionState: ExecutionState = {
    startupId: 1,
    phase: "PLAN",
    day: 0,
    week: 1,
    isRunning: false,
    isPaused: false,
    isCompleted: false,
    overallProgress: 0,
    mvpProgress: 0,
    technicalProgress: 0,
    marketReadiness: 0,
    financialHealth: 0,
    budgetRemaining: 100000,
    runwayMonths: 12,
    activeBlockerCount: 0,
    departmentProgress: {},
    tasks: [],
  };

  it("returns false before autonomous execution starts", () => {
    expect(isExecutionLifecycle(baseStartup, null)).toBe(false);
    expect(isExecutionLifecycle(baseStartup, emptyExecutionState)).toBe(false);

    const completedPlanningStartup = { ...baseStartup, currentPhase: "COMPLETED" as const, simulationCompleted: true };
    expect(isExecutionLifecycle(completedPlanningStartup, emptyExecutionState)).toBe(false);
  });

  it("returns true when startup currentPhase is EXECUTION", () => {
    const executionStartup = { ...baseStartup, currentPhase: "EXECUTION" as const };
    expect(isExecutionLifecycle(executionStartup, null)).toBe(true);
    expect(isExecutionLifecycle(executionStartup, emptyExecutionState)).toBe(true);
  });

  it("returns true when executionState is running, paused, or completed", () => {
    expect(isExecutionLifecycle(baseStartup, { ...emptyExecutionState, isRunning: true })).toBe(true);
    expect(isExecutionLifecycle(baseStartup, { ...emptyExecutionState, isPaused: true })).toBe(true);
    expect(isExecutionLifecycle(baseStartup, { ...emptyExecutionState, isCompleted: true })).toBe(true);
  });

  it("returns true when executionState has tasks or progress", () => {
    const withTasks: ExecutionState = {
      ...emptyExecutionState,
      tasks: [
        {
          id: 10,
          startupId: 1,
          department: "DEVELOPMENT",
          assignedAgent: "Lead Dev",
          title: "Setup CI/CD Pipeline",
          description: "Configure GitHub Actions",
          priority: "HIGH",
          status: "IN_PROGRESS",
          progress: 25,
          dependencies: [],
          estimatedDays: 3,
          actualDays: 1,
          blockerReason: null,
        },
      ],
    };
    expect(isExecutionLifecycle(baseStartup, withTasks)).toBe(true);
    expect(isExecutionLifecycle(baseStartup, { ...emptyExecutionState, overallProgress: 19.9 })).toBe(true);
    expect(isExecutionLifecycle(baseStartup, { ...emptyExecutionState, mvpProgress: 10 })).toBe(true);
  });

  it("correctly counts planning tasks", () => {
    const tasks: AgentTask[] = [
      { id: 1, agentType: "CEO", title: "Strategy", description: "", status: "COMPLETED", priority: "HIGH", progress: 100, estimatedCompletion: null },
      { id: 2, agentType: "DEVELOPMENT", title: "Architecture", description: "", status: "COMPLETED", priority: "HIGH", progress: 100, estimatedCompletion: null },
      { id: 3, agentType: "MARKETING", title: "Target Market", description: "", status: "COMPLETED", priority: "MEDIUM", progress: 100, estimatedCompletion: null },
      { id: 4, agentType: "FINANCE", title: "Unit Economics", description: "", status: "IN_PROGRESS", priority: "MEDIUM", progress: 60, estimatedCompletion: null },
    ];
    const stats = getPlanningTaskStats(tasks);
    expect(stats.completedCount).toBe(3);
    expect(stats.totalCount).toBe(4);
  });

  it("correctly counts execution tasks", () => {
    const tasks: ExecutionTask[] = [
      { id: 1, startupId: 1, department: "DEVELOPMENT", assignedAgent: "Dev", title: "T1", description: "", priority: "HIGH", status: "COMPLETED", progress: 100, dependencies: [], estimatedDays: 2, actualDays: 2 },
      { id: 2, startupId: 1, department: "DEVELOPMENT", assignedAgent: "Dev", title: "T2", description: "", priority: "HIGH", status: "BLOCKED", progress: 40, dependencies: [], estimatedDays: 3, actualDays: 1, blockerReason: "API quota exceeded" },
      { id: 3, startupId: 1, department: "MARKETING", assignedAgent: "Mkt", title: "T3", description: "", priority: "LOW", status: "PENDING", progress: 0, dependencies: [], estimatedDays: 2, actualDays: 0 },
    ];
    const stats = getExecutionTaskStats(tasks);
    expect(stats.completedCount).toBe(1);
    expect(stats.totalCount).toBe(3);
  });
});
