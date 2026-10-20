// @vitest-environment happy-dom
// @ts-expect-error React 18 testing flag
globalThis.IS_REACT_ACT_ENVIRONMENT = true;
import { describe, it, expect, beforeEach, afterEach } from "vitest";
import React, { act } from "react";
import { createRoot } from "react-dom/client";
import TasksSidebar from "./TasksSidebar";
import { useSim } from "../../store/simulationStore";
import type { AgentTask, ExecutionState, ExecutionTask, Startup } from "../../types";

const mockStartup: Startup = {
  id: 42,
  name: "Nova Cloud",
  originalIdea: "Serverless edge compute",
  currentPhase: "COMPLETED",
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
    overallProgress: 100,
    mvpProgress: 0,
    technicalFeasibility: 85,
    marketReadiness: 80,
    financialHealth: 90,
    budgetRemaining: 150000,
    runwayMonths: 18,
  },
  simulationStarted: true,
  simulationCompleted: true,
  createdAt: "2026-10-18T00:00:00Z",
  updatedAt: "2026-10-18T00:00:00Z",
};

const mockPlanningTasks: AgentTask[] = [
  { id: 1, agentType: "CEO", title: "Strategic Vision & Objectives", description: null, status: "COMPLETED", priority: "HIGH", progress: 100, estimatedCompletion: null },
  { id: 2, agentType: "DEVELOPMENT", title: "Technical Feasibility Assessment", description: null, status: "COMPLETED", priority: "HIGH", progress: 100, estimatedCompletion: null },
  { id: 3, agentType: "MARKETING", title: "Target Market Identification", description: null, status: "COMPLETED", priority: "MEDIUM", progress: 100, estimatedCompletion: null },
  { id: 4, agentType: "FINANCE", title: "Financial Projections & Runway", description: null, status: "COMPLETED", priority: "HIGH", progress: 100, estimatedCompletion: null },
];

const mockExecutionTasks: ExecutionTask[] = [
  {
    id: 101,
    startupId: 42,
    department: "DEVELOPMENT",
    assignedAgent: "Lead Engineer",
    title: "Build Distributed Edge Gateway",
    description: "Core packet routing engine",
    priority: "CRITICAL",
    status: "BLOCKED",
    progress: 25,
    dependencies: [],
    estimatedDays: 14,
    actualDays: 5,
    blockerReason: "Blocked on upstream cloud credentials and SSL cert provisioning",
  },
  {
    id: 102,
    startupId: 42,
    department: "MARKETING",
    assignedAgent: "Growth Lead",
    title: "Launch Developer Beta Landing Page",
    description: "Signups and waitlist onboarding",
    priority: "HIGH",
    status: "IN_PROGRESS",
    progress: 50,
    dependencies: [],
    estimatedDays: 7,
    actualDays: 3,
    blockerReason: null,
  },
  {
    id: 103,
    startupId: 42,
    department: "FINANCE",
    assignedAgent: "Financial Analyst",
    title: "Setup Stripe Billing Integration",
    description: "Usage-based tier pricing",
    priority: "MEDIUM",
    status: "COMPLETED",
    progress: 100,
    dependencies: [],
    estimatedDays: 5,
    actualDays: 5,
    blockerReason: null,
  },
  {
    id: 104,
    startupId: 42,
    department: "CEO",
    assignedAgent: "CEO",
    title: "Strategic Angel Investor Pitch Deck",
    description: "Seed round narrative",
    priority: "HIGH",
    status: "PENDING",
    progress: 0,
    dependencies: [],
    estimatedDays: 10,
    actualDays: 0,
    blockerReason: null,
  },
];

const mockExecutionState: ExecutionState = {
  startupId: 42,
  phase: "EXECUTION",
  day: 12,
  week: 2,
  isRunning: true,
  isPaused: false,
  isCompleted: false,
  overallProgress: 19.9,
  mvpProgress: 15.0,
  technicalProgress: 25.0,
  marketReadiness: 30.0,
  financialHealth: 75.0,
  budgetRemaining: 120000,
  runwayMonths: 15,
  activeBlockerCount: 1,
  departmentProgress: { DEVELOPMENT: 25, MARKETING: 50, FINANCE: 100, CEO: 0 },
  tasks: mockExecutionTasks,
};

let cleanupFns: Array<() => void> = [];

function render(ui: React.ReactElement) {
  const container = document.createElement("div");
  document.body.appendChild(container);
  const root = createRoot(container);
  act(() => {
    root.render(ui);
  });
  const cleanup = () => {
    act(() => {
      root.unmount();
    });
    container.remove();
  };
  cleanupFns.push(cleanup);
  return {
    container,
    html: container.innerHTML,
  };
}

describe("TasksSidebar Component", () => {
  beforeEach(() => {
    cleanupFns = [];
    document.body.innerHTML = "";
    useSim.getState().reset();
  });

  afterEach(() => {
    cleanupFns.forEach((fn) => fn());
    cleanupFns = [];
  });

  it("renders planning-task view before autonomous execution starts", () => {
    useSim.setState({
      startup: { ...mockStartup, currentPhase: "PLAN", simulationCompleted: false },
      tasks: mockPlanningTasks,
      executionState: null,
    });

    const { html } = render(<TasksSidebar />);

    // Header badge must clearly state "Planning Tasks"
    expect(html).toContain("Planning Tasks");
    expect(html).not.toContain("Execution Tasks");

    // Header count must reflect the 4 planning tasks
    expect(html).toContain("4/4 complete");

    // Planning task titles must be rendered
    expect(html).toContain("Strategic Vision &amp; Objectives");
    expect(html).toContain("Technical Feasibility Assessment");
    expect(html).toContain("Target Market Identification");
    expect(html).toContain("Financial Projections &amp; Runway");

    // Must NOT render execution tasks
    expect(html).not.toContain("Build Distributed Edge Gateway");
  });

  it("renders real execution tasks once execution is active", () => {
    useSim.setState({
      startup: { ...mockStartup, currentPhase: "EXECUTION" },
      tasks: mockPlanningTasks,
      executionState: mockExecutionState,
    });

    const { html } = render(<TasksSidebar />);

    // Header badge must clearly state "Execution Tasks"
    expect(html).toContain("Execution Tasks");

    // Header count must reflect execution tasks (1/4 complete: Stripe billing done, others in progress/blocked/pending)
    expect(html).toContain("1/4 complete");

    // Real execution task titles must be rendered
    expect(html).toContain("Build Distributed Edge Gateway");
    expect(html).toContain("Launch Developer Beta Landing Page");
    expect(html).toContain("Setup Stripe Billing Integration");
    expect(html).toContain("Strategic Angel Investor Pitch Deck");

    // Assigned agents must be displayed
    expect(html).toContain("Lead Engineer");
    expect(html).toContain("Growth Lead");

    // Accurate progress values must be rendered without fabrication
    expect(html).toContain("25%");
    expect(html).toContain("50%");
    expect(html).toContain("100%");
    expect(html).toContain("0%");
  });

  it("displays blocker reason and blocked status for blocked execution tasks", () => {
    useSim.setState({
      startup: { ...mockStartup, currentPhase: "EXECUTION" },
      tasks: mockPlanningTasks,
      executionState: mockExecutionState,
    });

    const { html } = render(<TasksSidebar />);

    // Blocked task status must be displayed
    expect(html).toContain("blocked");

    // Blocker reason must be displayed prominently
    expect(html).toContain("Blocked on upstream cloud credentials and SSL cert provisioning");
    expect(html).toContain("Blocker:");
  });

  it("handles completed execution accurately", () => {
    const completedTasks: ExecutionTask[] = mockExecutionTasks.map((t) => ({
      ...t,
      status: "COMPLETED",
      progress: 100,
      blockerReason: null,
    }));

    useSim.setState({
      startup: { ...mockStartup, currentPhase: "EXECUTION" },
      tasks: mockPlanningTasks,
      executionState: {
        ...mockExecutionState,
        isCompleted: true,
        isRunning: false,
        overallProgress: 100,
        activeBlockerCount: 0,
        tasks: completedTasks,
      },
    });

    const { html } = render(<TasksSidebar />);

    expect(html).toContain("Execution Tasks");
    expect(html).toContain("4/4 complete");
    expect(html).not.toContain("Blocker:");
  });

  it("handles paused execution state without reverting to planning tasks", () => {
    useSim.setState({
      startup: { ...mockStartup, currentPhase: "EXECUTION" },
      tasks: mockPlanningTasks,
      executionState: {
        ...mockExecutionState,
        isRunning: false,
        isPaused: true,
      },
    });

    const { html } = render(<TasksSidebar />);

    expect(html).toContain("Execution Tasks");
    expect(html).toContain("1/4 complete");
    expect(html).toContain("Build Distributed Edge Gateway");
    expect(html).not.toContain("Strategic Vision &amp; Objectives");
  });

  it("handles loading execution state during refresh without falling back to planning tasks", () => {
    useSim.setState({
      startup: { ...mockStartup, currentPhase: "EXECUTION" },
      tasks: mockPlanningTasks,
      executionState: null, // executionState loading in flight
    });

    const { html } = render(<TasksSidebar />);

    // Must remain on Execution Tasks lifecycle
    expect(html).toContain("Execution Tasks");
    expect(html).toContain("Loading execution tasks…");

    // Must NOT flash planning tasks
    expect(html).not.toContain("Strategic Vision &amp; Objectives");
  });

  it("handles empty execution tasks state without falling back to planning tasks", () => {
    useSim.setState({
      startup: { ...mockStartup, currentPhase: "EXECUTION" },
      tasks: mockPlanningTasks,
      executionState: {
        ...mockExecutionState,
        tasks: [],
      },
    });

    const { html } = render(<TasksSidebar />);

    expect(html).toContain("Execution Tasks");
    expect(html).toContain("Autonomous Execution Initialized");
    expect(html).toContain("Preparing roadmap tasks…");
    expect(html).not.toContain("Strategic Vision &amp; Objectives");
  });

  it("allows switching tabs between Execution and Planning when execution exists", () => {
    useSim.setState({
      startup: { ...mockStartup, currentPhase: "EXECUTION" },
      tasks: mockPlanningTasks,
      executionState: mockExecutionState,
    });

    const { container } = render(<TasksSidebar />);

    // Defaults to Execution Tasks
    expect(container.innerHTML).toContain("Execution Tasks");
    expect(container.innerHTML).toContain("Build Distributed Edge Gateway");

    // Click Planning Tab
    const planningTab = container.querySelector('[data-testid="tab-planning"]') as HTMLButtonElement;
    expect(planningTab).not.toBeNull();
    act(() => {
      planningTab.click();
    });

    // Now switched to Planning Tasks
    expect(container.innerHTML).toContain("Planning Tasks");
    expect(container.innerHTML).toContain("Strategic Vision &amp; Objectives");
    expect(container.innerHTML).toContain("4/4 complete");
    expect(container.innerHTML).not.toContain("Build Distributed Edge Gateway");

    // Click Execution Tab to switch back
    const executionTab = container.querySelector('[data-testid="tab-execution"]') as HTMLButtonElement;
    expect(executionTab).not.toBeNull();
    act(() => {
      executionTab.click();
    });

    // Now back to Execution Tasks
    expect(container.innerHTML).toContain("Execution Tasks");
    expect(container.innerHTML).toContain("Build Distributed Edge Gateway");
    expect(container.innerHTML).toContain("1/4 complete");
  });
});
