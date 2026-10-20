// Mirrors the backend DTOs (com.startupsimulator.dto.response) exactly. The
// backend is the single source of truth; these are read-only view shapes.

export type SimulationPhase =
  | "IDEA"
  | "ANALYSIS"
  | "DEBATE"
  | "DECISION"
  | "PLAN"
  | "COMPLETED"
  | "EXECUTION";

export type AgentType = "CEO" | "DEVELOPMENT" | "MARKETING" | "FINANCE";

export type AgentState =
  | "IDLE"
  | "THINKING"
  | "WORKING"
  | "DISCUSSING"
  | "WAITING"
  | "DECIDING"
  | "COMPLETED"
  | "WALKING"
  | "BLOCKED"
  | "MEETING";

export type TaskStatus = "PENDING" | "IN_PROGRESS" | "BLOCKED" | "COMPLETED";
export type TaskPriority = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
export type DebateStatus = "OPEN" | "RESOLVED" | "COMPLETED_WITH_WARNINGS";
export type DecisionStatus = "PROPOSED" | "APPROVED" | "REJECTED";

export type EventType =
  | "STARTUP_CREATED"
  | "AGENT_INITIALIZED"
  | "TASK_CREATED"
  | "AGENT_STARTED_WORK"
  | "AGENT_PROGRESS_UPDATED"
  | "AGENT_MESSAGE_CREATED"
  | "DEBATE_STARTED"
  | "AGENT_JOINED_DEBATE"
  | "AGENT_RESPONDED"
  | "DECISION_PROPOSED"
  | "DECISION_APPROVED"
  | "MVP_UPDATED"
  | "BUDGET_UPDATED"
  | "PHASE_COMPLETED"
  | "BLUEPRINT_GENERATED"
  // ---- Phase 2A: real LLM-powered CEO agent ----
  | "CEO_ANALYSIS_STARTED"
  | "CEO_ANALYSIS_COMPLETED"
  | "CEO_ANALYSIS_FAILED"
  | "LLM_REQUEST_STARTED"
  | "LLM_RESPONSE_RECEIVED"
  // ---- Phase 2B: real LLM-powered department agents ----
  | "AGENT_ANALYSIS_STARTED"
  | "AGENT_ANALYSIS_COMPLETED"
  | "AGENT_ANALYSIS_FAILED"
  // ---- Phase 2C: autonomous boardroom debate + CEO synthesis ----
  | "DEBATE_ROUND_STARTED"
  | "DEBATE_ROUND_COMPLETED"
  | "AGENT_DEBATE_STARTED"
  | "AGENT_DEBATE_MESSAGE"
  | "AGENT_DEBATE_COMPLETED"
  | "DEBATE_COMPLETED"
  | "CEO_SYNTHESIS_STARTED"
  | "CEO_SYNTHESIS_COMPLETED"
  | "DECISION_STARTED"
  | "DECISION_COMPLETED"
  // ---- Phase 2F: Autonomous Startup Execution ----
  | "EXECUTION_STARTED"
  | "TASK_ASSIGNED"
  | "TASK_STARTED"
  | "TASK_PROGRESS"
  | "TASK_COMPLETED"
  | "TASK_BLOCKED"
  | "EMPLOYEE_STARTED_WORK"
  | "EMPLOYEE_FINISHED_WORK"
  | "METRIC_CHANGED"
  | "CEO_INTERVENTION_REQUIRED"
  | "EXECUTION_PAUSED"
  | "EXECUTION_RESUMED"
  | "EXECUTION_COMPLETED";

export interface ExecutionTask {
  id: number;
  startupId: number;
  department: AgentType;
  assignedAgent: string;
  title: string;
  description: string;
  priority: TaskPriority;
  status: TaskStatus;
  progress: number;
  dependencies: number[];
  estimatedDays: number;
  actualDays: number;
  blockerReason?: string | null;
}

export interface ExecutionState {
  startupId: number;
  phase: SimulationPhase;
  day: number;
  week: number;
  isRunning: boolean;
  isPaused: boolean;
  isCompleted: boolean;
  overallProgress: number;
  mvpProgress: number;
  technicalProgress: number;
  marketReadiness: number;
  financialHealth: number;
  budgetRemaining: number;
  runwayMonths: number;
  activeBlockerCount: number;
  departmentProgress: Record<string, number>;
  tasks: ExecutionTask[];
}

export interface StartupHealth {
  overallProgress: number;
  mvpProgress: number;
  technicalFeasibility: number;
  marketReadiness: number;
  financialHealth: number;
  budgetRemaining: number;
  runwayMonths: number;
}

export interface Startup {
  id: number;
  name: string;
  originalIdea: string;
  currentPhase: SimulationPhase;
  problem: string | null;
  solution: string | null;
  targetAudience: string | null;
  valueProposition: string | null;
  businessModel: string | null;
  constraints: string | null;
  // ---- CEO strategic analysis (Phase 2A) ----
  executiveSummary: string | null;
  strategicObjectives: string[];
  assumptions: string[];
  mvpDirection: string | null;
  ceoAnalysisProvider: string | null;
  ceoAnalysisFailed: boolean;
  ceoAnalysisError: string | null;
  health: StartupHealth;
  simulationStarted: boolean;
  simulationCompleted: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface Agent {
  id: number;
  type: AgentType;
  typeLabel: string;
  name: string;
  state: AgentState;
  currentActivity: string | null;
  mandate: string;
}

export interface AgentTask {
  id: number;
  agentType: AgentType;
  title: string;
  description: string | null;
  status: TaskStatus;
  priority: TaskPriority;
  progress: number;
  estimatedCompletion: string | null;
}

export interface AgentMessage {
  id: number;
  agentType: AgentType;
  agentLabel: string;
  debateId: number | null;
  // ---- Phase 2C debate metadata (null for non-debate messages) ----
  debateRound: number | null;
  messageType: string | null;
  targetAgent: AgentType | null;
  content: string;
  createdAt: string;
}

export interface Decision {
  id: number;
  debateId: number | null;
  decidedBy: AgentType;
  decision: string;
  reason: string;
  affectedDepartments: string[];
  status: DecisionStatus;
  // ---- Phase 2C: structured CEO synthesis of the boardroom debate ----
  finalMvpDirection: string | null;
  acceptedArguments: string[];
  rejectedArguments: string[];
  finalRisks: string[];
  finalPriorities: string[];
  createdAt: string;
}

export interface Debate {
  id: number;
  topic: string;
  question: string;
  status: DebateStatus;
  startedAt: string;
  resolvedAt: string | null;
  messages: AgentMessage[];
  decision: Decision | null;
}

export interface StartupEvent {
  id: number;
  startupId: number;
  type: EventType;
  message: string;
  payload: string | null;
  createdAt: string;
}

export interface MvpFeature {
  id: number;
  name: string;
  description: string;
  inMvp: boolean;
  targetRelease: string;
  effort: number;
}

export interface TechnicalPlan {
  architecture: string;
  techStack: string;
  timeline: string;
  technicalRisks: string;
  estimatedEngineeringMonths: number;
  analysisProvider: string | null;
  analysisFailed: boolean;
  analysisError: string | null;
}

export interface MarketingPlan {
  positioning: string;
  pricingStrategy: string;
  channels: string;
  competitorAnalysis: string;
  goToMarket: string;
  analysisProvider: string | null;
  analysisFailed: boolean;
  analysisError: string | null;
}

export interface Budget {
  developmentCost: number;
  infrastructureCost: number;
  marketingBudget: number;
  operatingCost: number;
  totalUpfrontCost: number;
  startingCapital: number;
  monthlyBurn: number;
  projectedMonthlyRevenue: number;
  runwayMonths: number;
  breakEvenAssumption: string;
  analysisProvider: string | null;
  analysisFailed: boolean;
  analysisError: string | null;
}

// Aggregated department plans (GET /api/startups/{id}/plans). Any plan may be
// null if that department has not produced an analysis yet.
export interface Plans {
  technical: TechnicalPlan | null;
  marketing: MarketingPlan | null;
  budget: Budget | null;
}

export interface RoadmapMilestone {
  id: number;
  phase: string;
  title: string;
  description: string;
  timeframe: string;
}

export interface ExecutiveSummarySection {
  startupName: string;
  pitch: string;
  problem: string;
  solution: string;
  targetCustomer: string;
  valueProposition: string;
  businessModel: string;
  strategicObjectives: string[];
  mvpDirection: string | null;
  source: string;
}

export interface ProblemSection {
  problemStatement: string;
  targetCustomer: string;
  customerPainPoints: string[];
  currentAlternatives: string[];
  assumptions: string[];
  source: string;
}

export interface SolutionSection {
  productDescription: string;
  coreValueProposition: string;
  primaryUserWorkflow: string[];
  keyDifferentiators: string[];
  source: string;
}

export interface MvpFeatureDetail {
  id: number;
  name: string;
  description: string;
  inMvp: boolean;
  targetRelease: string;
  effort: number;
  priority: string;
  reason: string;
  sourceDepartment: string;
}

export interface MvpSection {
  mvpFeatures: MvpFeatureDetail[];
  deferredFeatures: MvpFeatureDetail[];
  source: string;
}

export interface TechnicalSection {
  architecture: string;
  frontend: string;
  backend: string;
  database: string;
  aiMl: string;
  integrations: string;
  infrastructure: string;
  technicalRisks: string[];
  developmentTimeline: string;
  estimatedEngineeringMonths: number;
  source: string;
}

export interface GtmSection {
  targetSegment: string;
  persona: string;
  positioning: string;
  messaging: string;
  acquisitionChannels: string[];
  launchStrategy: string;
  initialLaunchPlan: string[];
  marketingRisks: string[];
  assumptions: string[];
  source: string;
}

export interface FinancialSection {
  startupCosts: Record<string, number>;
  startingCapital: number;
  totalUpfrontCost: number;
  monthlyOperatingCosts: number;
  monthlyBurn: number;
  budgetAllocation: Record<string, string>;
  pricingModel: string;
  revenueAssumptions: string[];
  runwayMonths: number;
  breakEvenConsiderations: string;
  financialRisks: string[];
  source: string;
}

export interface BoardroomDecisionItem {
  id: number;
  decision: string;
  finalStatus: string;
  supportingArguments: string[];
  opposingArguments: string[];
  departmentsInvolved: string[];
  ceoResolution: string;
  reason: string;
}

export interface BoardroomDecisionsSection {
  disagreements: string[];
  debateChanges: string[];
  ceoDecisionSummary: string;
  decisions: BoardroomDecisionItem[];
  source: string;
}

export interface ReasoningStep {
  stepNumber: number;
  stage: string;
  title: string;
  description: string;
  department: string;
}

export interface WhyThisPlanSection {
  summary: string;
  reasoningChain: ReasoningStep[];
  source: string;
}

export interface RiskItem {
  description: string;
  source: string;
}

export interface RisksSection {
  aggregatedRisks: RiskItem[];
  source: string;
}

export interface AssumptionItem {
  statement: string;
  category: string;
  source: string;
}

export interface AssumptionsSection {
  aggregatedAssumptions: AssumptionItem[];
  source: string;
}

export interface ExecutionStage {
  stageName: string;
  timeframe: string;
  focus: string;
  milestones: RoadmapMilestone[];
  keyDeliverables: string[];
  responsibleDepartments: string[];
}

export interface ExecutionPlan90Day {
  overview: string;
  stages: ExecutionStage[];
  source: string;
}

export interface Blueprint {
  startupId: number;
  name: string;
  pitch: string;
  problem: string;
  solution: string;
  targetCustomer: string;
  valueProposition: string;
  businessModel: string;
  mvp: MvpFeature[];
  backlog: MvpFeature[];
  technical: TechnicalPlan;
  marketing: MarketingPlan;
  budget: Budget;
  revenueModel: string;
  roadmap: RoadmapMilestone[];
  risks: string[];
  decisions: Decision[];
  complete: boolean;
  // ---- Phase 2D: Rich, traceable blueprint sections --------------------
  executiveSummarySection?: ExecutiveSummarySection;
  problemSection?: ProblemSection;
  solutionSection?: SolutionSection;
  mvpSection?: MvpSection;
  technicalSection?: TechnicalSection;
  gtmSection?: GtmSection;
  financialSection?: FinancialSection;
  boardroomSection?: BoardroomDecisionsSection;
  whyThisPlanSection?: WhyThisPlanSection;
  risksSection?: RisksSection;
  assumptionsSection?: AssumptionsSection;
  executionPlanSection?: ExecutionPlan90Day;
}

export interface ExecutionTraceEntry {
  id: number;
  startupId: number;
  traceId: string;
  sequenceNumber: number;
  timestamp: string;
  type: string;
  agent: string | null;
  sender: string | null;
  recipient: string | null;
  action: string | null;
  decision: string | null;
  reason: string | null;
  toolName: string | null;
  toolArguments: string | null;
  toolResult: string | null;
  outcome: string | null;
  payload: string | null;
}

export type GeneratedAppStatus = "UNGENERATED" | "GENERATING" | "GENERATED" | "BUILDING" | "READY" | "FAILED";
export type GeneratedBuildStatus = "NOT_BUILT" | "BUILDING" | "SUCCESS" | "FAILED";

export interface GeneratedApp {
  id: number | null;
  startupId: number;
  appName: string | null;
  template: string;
  status: GeneratedAppStatus;
  buildStatus: GeneratedBuildStatus;
  buildOutput: string | null;
  previewPort: number | null;
  previewUrl: string | null;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface GeneratedFileInfo {
  path: string;
  name: string;
  sizeBytes: number;
  isDirectory: boolean;
  fileType: string;
}

export interface GeneratedFileContent {
  path: string;
  name: string;
  content: string;
  sizeBytes: number;
}

export interface ProposedFileChange {
  relativePath: string;
  action: "CREATE" | "MODIFY" | "DELETE";
  description: string;
  content: string;
}

export type ChangeRequestStatus = "PLANNING" | "PROPOSED" | "APPROVED" | "APPLYING" | "SUCCEEDED" | "FAILED" | "REJECTED";

export interface AppChangeRequest {
  id: number;
  startupId: number;
  userPrompt: string;
  agentType: AgentType;
  agentName: string;
  status: ChangeRequestStatus;
  planSummary: string;
  proposedChanges: ProposedFileChange[];
  buildOutput: string | null;
  errorMessage: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CoworkerChatMessage {
  id: number;
  startupId: number;
  role: "USER" | "ASSISTANT" | "SYSTEM";
  agentType: AgentType | null;
  agentName: string | null;
  content: string;
  changeRequestId: number | null;
  changeRequest?: AppChangeRequest | null;
  createdAt: string;
}
