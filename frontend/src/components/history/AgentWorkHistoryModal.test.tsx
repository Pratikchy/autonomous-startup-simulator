// @vitest-environment happy-dom
// @ts-expect-error React 18 testing flag
globalThis.IS_REACT_ACT_ENVIRONMENT = true;
import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import React, { act } from "react";
import { createRoot } from "react-dom/client";
import AgentWorkHistoryModal from "./AgentWorkHistoryModal";
import { useSim } from "../../store/simulationStore";
import type { Agent, AgentMessage, Debate, Decision, ExecutionState, ExecutionTask, ExecutionTraceEntry, Startup } from "../../types";

const mockStartup: Startup = {
  id: 2,
  name: "NPWR Automation",
  originalIdea: "Autonomous clean energy scheduler",
  currentPhase: "EXECUTION",
  problem: "Inefficient grid balancing",
  solution: "AI-driven demand response",
  targetAudience: "Industrial plants",
  valueProposition: "Cut power peak spend by 35%",
  businessModel: "SaaS + Savings share",
  constraints: "Strict ISO compliance",
  executiveSummary: "Scaling autonomous energy management",
  strategicObjectives: ["Deploy MVP grid agent", "Close 3 pilots"],
  assumptions: ["Real-time meters available"],
  mvpDirection: "Web dashboard + control API",
  ceoAnalysisProvider: "LLM",
  ceoAnalysisFailed: false,
  ceoAnalysisError: null,
  health: {
    overallProgress: 35.9,
    mvpProgress: 30.0,
    technicalFeasibility: 85,
    marketReadiness: 70,
    financialHealth: 88,
    budgetRemaining: 120000,
    runwayMonths: 14,
  },
  simulationStarted: true,
  simulationCompleted: true,
  createdAt: "2026-10-18T10:00:00Z",
  updatedAt: "2026-10-19T10:00:00Z",
};

const mockAgents: Agent[] = [
  { id: 1, type: "CEO", typeLabel: "CEO", name: "Alex Vance", mandate: "Overall strategic vision and capital allocation", state: "IDLE", currentActivity: "Reviewing Q2 roadmaps" },
  { id: 2, type: "DEVELOPMENT", typeLabel: "Engineering", name: "Dev Lead & Backend Eng", mandate: "Architecture, database, and backend implementation", state: "WORKING", currentActivity: "Implementing Redis caching tier" },
  { id: 3, type: "MARKETING", typeLabel: "Marketing", name: "Growth Lead", mandate: "Acquisition, branding, and community", state: "IDLE", currentActivity: "Finalizing developer beta campaign" },
  { id: 4, type: "FINANCE", typeLabel: "Finance", name: "Finance Director", mandate: "Runway, burn rate, and financial models", state: "IDLE", currentActivity: "Auditing cloud provider spend" },
];

const mockExecutionTasks: ExecutionTask[] = [
  {
    id: 101,
    startupId: 2,
    department: "DEVELOPMENT",
    assignedAgent: "Lead Engineer",
    title: "Core Database & API Infrastructure",
    description: "Postgres schema and REST endpoints",
    priority: "CRITICAL",
    status: "IN_PROGRESS",
    progress: 45,
    dependencies: [],
    estimatedDays: 14,
    actualDays: 6,
    blockerReason: null,
  },
  {
    id: 102,
    startupId: 2,
    department: "DEVELOPMENT",
    assignedAgent: "Lead Engineer",
    title: "System Architecture & Tech Stack Setup",
    description: "Repo bootstrap and docker-compose",
    priority: "HIGH",
    status: "COMPLETED",
    progress: 100,
    dependencies: [],
    estimatedDays: 7,
    actualDays: 7,
    blockerReason: null,
  },
  {
    id: 103,
    startupId: 2,
    department: "MARKETING",
    assignedAgent: "Growth Lead",
    title: "Landing Page & Developer Waitlist",
    description: "Conversion funnels for beta",
    priority: "HIGH",
    status: "COMPLETED",
    progress: 100,
    dependencies: [],
    estimatedDays: 5,
    actualDays: 5,
    blockerReason: null,
  },
  {
    id: 104,
    startupId: 2,
    department: "FINANCE",
    assignedAgent: "Finance Director",
    title: "90-Day Budget & Runway Model",
    description: "Cap table and burn model",
    priority: "HIGH",
    status: "COMPLETED",
    progress: 100,
    dependencies: [],
    estimatedDays: 4,
    actualDays: 4,
    blockerReason: null,
  },
];

const mockExecutionState: ExecutionState = {
  startupId: 2,
  phase: "EXECUTION",
  day: 14,
  week: 2,
  isRunning: false,
  isPaused: true,
  isCompleted: false,
  overallProgress: 35.9,
  mvpProgress: 30.0,
  technicalProgress: 45.0,
  marketReadiness: 50.0,
  financialHealth: 88.0,
  budgetRemaining: 120000,
  runwayMonths: 14,
  activeBlockerCount: 0,
  departmentProgress: { DEVELOPMENT: 45, MARKETING: 50, FINANCE: 100, CEO: 100 },
  tasks: mockExecutionTasks,
};

const mockTraces: ExecutionTraceEntry[] = [
  {
    id: 201,
    startupId: 2,
    traceId: "tr-201",
    sequenceNumber: 1,
    timestamp: "2026-10-19T08:30:00Z",
    type: "TOOL_CALL",
    agent: "DEVELOPMENT",
    sender: null,
    recipient: null,
    action: "Invoked PostgresSchemaGenerator tool for energy meters table",
    decision: null,
    reason: "Require robust schema for high-frequency telemetry",
    toolName: "PostgresSchemaGenerator",
    toolArguments: '{"table":"energy_meters"}',
    toolResult: '{"status":"CREATED"}',
    outcome: "SUCCESS",
    payload: '{"table":"energy_meters","fields":["id","device_id","kw_reading"]}',
  },
  {
    id: 202,
    startupId: 2,
    traceId: "tr-202",
    sequenceNumber: 2,
    timestamp: "2026-10-19T08:45:00Z",
    type: "TASK_STATE_CHANGE",
    agent: "DEVELOPMENT",
    sender: null,
    recipient: null,
    action: "Task #101 Core Database & API Infrastructure advanced to 45%",
    decision: null,
    reason: "Applied migration and health endpoint",
    toolName: null,
    toolArguments: null,
    toolResult: null,
    outcome: "IN_PROGRESS",
    payload: '{"taskId":101,"status":"IN_PROGRESS","progress":45}',
  },
  {
    id: 203,
    startupId: 2,
    traceId: "tr-203",
    sequenceNumber: 3,
    timestamp: "2026-10-18T16:00:00Z",
    type: "ORCHESTRATION_DECISION",
    agent: "CEO",
    sender: null,
    recipient: null,
    action: "Prioritized backend scalability over early mobile client",
    decision: "Focus on Enterprise API",
    reason: "Enterprise pilots require robust API before mobile views",
    toolName: null,
    toolArguments: null,
    toolResult: null,
    outcome: "DECIDED",
    payload: '{"decision":"focus_backend"}',
  },
  {
    id: 204,
    startupId: 2,
    traceId: "tr-204",
    sequenceNumber: 4,
    timestamp: "2026-10-17T14:20:00Z",
    type: "AGENT_MESSAGE",
    agent: "MARKETING",
    sender: "MARKETING",
    recipient: "CEO",
    action: "Published developer preview announcement on technical forum",
    decision: null,
    reason: "Gather developer beta signups",
    toolName: null,
    toolArguments: null,
    toolResult: null,
    outcome: "SENT",
    payload: '{"channel":"forum","upvotes":142}',
  },
  {
    id: 205,
    startupId: 2,
    traceId: "tr-205",
    sequenceNumber: 5,
    timestamp: "2026-10-16T11:15:00Z",
    type: "LLM_REASONING",
    agent: "FINANCE",
    sender: null,
    recipient: null,
    action: "Assessed cloud hosting unit economics under peak winter demand",
    decision: null,
    reason: "Calculate cloud elasticity buffer",
    toolName: null,
    toolArguments: null,
    toolResult: null,
    outcome: "COMPLETED",
    payload: '{"scenario":"winter_peak"}',
  },
];

const mockDecisions: Decision[] = [
  {
    id: 301,
    debateId: 1,
    decidedBy: "CEO",
    decision: "Adopt PostgreSQL + Redis event streaming for high-throughput telemetries",
    reason: "Selected robust relational data model with asynchronous queueing",
    affectedDepartments: ["DEVELOPMENT", "FINANCE"],
    status: "APPROVED",
    finalMvpDirection: "Web dashboard + control API",
    acceptedArguments: ["Strong ACID guarantees", "Sub-millisecond telemetry cache"],
    rejectedArguments: [],
    finalRisks: [],
    finalPriorities: ["Database scalability"],
    createdAt: "2026-10-18T12:00:00Z",
  },
];

const mockDebates: Debate[] = [
  {
    id: 1,
    topic: "Core Infrastructure Architecture",
    question: "Which database and cache topology should we adopt?",
    status: "RESOLVED",
    startedAt: "2026-10-18T10:00:00Z",
    resolvedAt: "2026-10-18T12:00:00Z",
    messages: [],
    decision: mockDecisions[0],
  },
];

const mockMessages: AgentMessage[] = [
  {
    id: 401,
    agentType: "DEVELOPMENT",
    agentLabel: "Engineering",
    debateId: null,
    debateRound: null,
    messageType: null,
    targetAgent: null,
    content: "Schema migration executed cleanly without downtime.",
    createdAt: "2026-10-19T09:00:00Z",
  },
];

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

describe("AgentWorkHistoryModal Component", () => {
  beforeEach(() => {
    cleanupFns = [];
    document.body.innerHTML = "";
    useSim.getState().reset();

    useSim.setState({
      startup: mockStartup,
      agents: mockAgents,
      trace: mockTraces,
      executionState: mockExecutionState,
      debates: mockDebates,
      messages: mockMessages,
      loadTrace: vi.fn().mockResolvedValue(undefined),
    });
  });

  afterEach(() => {
    cleanupFns.forEach((fn) => fn());
    cleanupFns = [];
  });

  it("renders modal header with startup scope and recorded facts disclosure badge", () => {
    const onClose = vi.fn();
    const { html } = render(<AgentWorkHistoryModal onClose={onClose} />);

    // Header title
    expect(html).toContain("Agent Work History &amp; Audit Trace");
    // Startup scoping: name and ID must appear
    expect(html).toContain("NPWR Automation");
    expect(html).toContain("#2");

    // Must clearly state provenance and authoritative DB facts
    expect(html).toContain("RECORDED FACTS:");
    expect(html).toContain("Authoritative database records");

    // Untracked notice must be present
    expect(html).toContain("NOT TRACKED:");
    expect(html).toContain("Git code diffs, real banking transactions");
  });

  it("renders the 4 agents in the filter selector bar", () => {
    const onClose = vi.fn();
    const { html } = render(<AgentWorkHistoryModal onClose={onClose} />);

    expect(html).toContain("All Agents Overview");
    expect(html).toContain("Chief Executive");
    expect(html).toContain("Engineering");
    expect(html).toContain("Growth &amp; Marketing");
    expect(html).toContain("Finance &amp; Treasury");
  });

  it("displays metrics summary strip for recorded work", () => {
    const onClose = vi.fn();
    const { html } = render(<AgentWorkHistoryModal onClose={onClose} />);

    expect(html).toContain("Tools:");
    expect(html).toContain("Tasks:");
    expect(html).toContain("Decisions:");
    expect(html).toContain("Messages:");
  });

  it("filters records strictly by agent when Development tab is clicked", () => {
    const onClose = vi.fn();
    const { container } = render(<AgentWorkHistoryModal onClose={onClose} />);

    // Find and click Development agent filter button
    const devBtn = container.querySelector('[data-testid="tab-agent-development"]') as HTMLButtonElement;
    expect(devBtn).not.toBeNull();

    act(() => {
      devBtn.click();
    });

    const html = container.innerHTML;
    // Development items must be present
    expect(html).toContain("Invoked PostgresSchemaGenerator tool for energy meters table");
    expect(html).toContain("Task #101 Core Database &amp; API Infrastructure advanced to 45%");

    // Other department actions should be filtered out from the list
    expect(html).not.toContain("Published developer preview announcement on technical forum");
    expect(html).not.toContain("Assessed cloud hosting unit economics under peak winter demand");
  });

  it("filters by category when 'Tools' button is clicked", () => {
    const onClose = vi.fn();
    const { container } = render(<AgentWorkHistoryModal onClose={onClose} />);

    // Find Tools category button
    const toolsBtn = container.querySelector('[data-testid="filter-cat-tools"]') as HTMLButtonElement;
    expect(toolsBtn).not.toBeNull();

    act(() => {
      toolsBtn.click();
    });

    const html = container.innerHTML;
    expect(html).toContain("Invoked PostgresSchemaGenerator tool for energy meters table");
    // Non-tools should be filtered out
    expect(html).not.toContain("Published developer preview announcement on technical forum");
  });

  it("filters by search query", () => {
    const onClose = vi.fn();
    const { container } = render(<AgentWorkHistoryModal onClose={onClose} />);

    const searchInput = container.querySelector('input[placeholder*="Search recorded"]') as HTMLInputElement;
    expect(searchInput).not.toBeNull();

    act(() => {
      const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value")?.set;
      setter?.call(searchInput, "PostgresSchemaGenerator");
      searchInput.dispatchEvent(new Event("input", { bubbles: true }));
    });

    const html = container.innerHTML;
    expect(html).toContain("Invoked PostgresSchemaGenerator tool for energy meters table");
    expect(html).not.toContain("Assessed cloud hosting unit economics under peak winter demand");
  });

  it("inspects a selected record and opens the detail inspector panel", () => {
    const onClose = vi.fn();
    const { container } = render(<AgentWorkHistoryModal onClose={onClose} />);

    // Click on the first record card in the timeline
    const recordCard = container.querySelector('[data-testid="trace-entry-201"]') as HTMLElement;
    expect(recordCard).not.toBeNull();

    act(() => {
      recordCard.click();
    });

    const html = container.innerHTML;
    // Detail inspector panel should now show payload and audit information
    expect(html).toContain("Record Details Inspector");
    expect(html).toContain("Timestamp &amp; DB Provenance");
    expect(html).toContain("Raw Verified Payload");
    expect(html).toContain("energy_meters");
  });

  it("invokes onClose when clicking the close button", () => {
    const onClose = vi.fn();
    const { container } = render(<AgentWorkHistoryModal onClose={onClose} />);

    const closeBtn = container.querySelector('[data-testid="close-history-modal"]') as HTMLButtonElement;
    expect(closeBtn).not.toBeNull();

    act(() => {
      closeBtn.click();
    });

    expect(onClose).toHaveBeenCalledTimes(1);
  });
});
