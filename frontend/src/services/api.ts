import type {
  Agent,
  AgentMessage,
  AgentTask,
  Blueprint,
  Debate,
  Decision,
  Plans,
  Startup,
  StartupEvent,
  ExecutionState,
  ExecutionTraceEntry,
  GeneratedApp,
  GeneratedFileInfo,
  GeneratedFileContent,
  AppChangeRequest,
  CoworkerChatMessage,
} from "../types";

// In dev, Vite proxies /api to the backend (see vite.config.ts), so a relative
// base works both in dev and when the SPA is served behind the same origin.
const BASE = (import.meta.env.VITE_API_BASE_URL as string | undefined)?.replace(/\/$/, "") || "/api";

async function req<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    headers: { "Content-Type": "application/json" },
    ...init,
  });
  if (!res.ok) {
    const body = await res.text().catch(() => "");
    throw new Error(`${res.status} ${res.statusText}${body ? ` — ${body}` : ""}`);
  }
  // 202 Accepted (simulate) may still carry a body; guard empty responses.
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export const api = {
  createStartup: (idea: string, name?: string) =>
    req<Startup>("/startups", {
      method: "POST",
      body: JSON.stringify({ idea, name: name || undefined }),
    }),

  listStartups: () => req<Startup[]>("/startups"),

  getStartup: (id: number) => req<Startup>(`/startups/${id}`),

  simulate: (id: number, speed = 1.0) =>
    req<Startup>(`/startups/${id}/simulate?speed=${speed}`, { method: "POST" }),

  // Re-run the CEO's strategic analysis (Phase 2A) after a failed/incomplete
  // LLM analysis. Runs asynchronously on the backend; the UI reacts via SSE.
  retryCeoAnalysis: (id: number) =>
    req<Startup>(`/startups/${id}/agents/ceo/analyze`, { method: "POST" }),

  // Re-run a single department's analysis (Phase 2B). `dept` is one of
  // developer | marketing | finance. Runs asynchronously; UI reacts via SSE.
  retryDepartmentAnalysis: (id: number, dept: "developer" | "marketing" | "finance") =>
    req<Startup>(`/startups/${id}/agents/${dept}/analyze`, { method: "POST" }),

  agents: (id: number) => req<Agent[]>(`/startups/${id}/agents`),
  tasks: (id: number) => req<AgentTask[]>(`/startups/${id}/tasks`),
  messages: (id: number) => req<AgentMessage[]>(`/startups/${id}/messages`),
  debates: (id: number) => req<Debate[]>(`/startups/${id}/debates`),
  decisions: (id: number) => req<Decision[]>(`/startups/${id}/decisions`),
  events: (id: number) => req<StartupEvent[]>(`/startups/${id}/events`),
  blueprint: (id: number) => req<Blueprint>(`/startups/${id}/blueprint`),
  plans: (id: number) => req<Plans>(`/startups/${id}/plans`),
  trace: (id: number, agent?: string) =>
    req<ExecutionTraceEntry[]>(`/startups/${id}/trace${agent ? `?agent=${encodeURIComponent(agent)}` : ""}`),

  // ---- Phase 2F: Autonomous Startup Execution ----
  startExecution: (id: number) =>
    req<ExecutionState>(`/startups/${id}/execution/start`, { method: "POST" }),
  pauseExecution: (id: number) =>
    req<ExecutionState>(`/startups/${id}/execution/pause`, { method: "POST" }),
  resumeExecution: (id: number) =>
    req<ExecutionState>(`/startups/${id}/execution/resume`, { method: "POST" }),
  stepExecution: (id: number) =>
    req<ExecutionState>(`/startups/${id}/execution/step`, { method: "POST" }),
  resolveTaskBlocker: (id: number, taskId: number) =>
    req<ExecutionState>(`/startups/${id}/execution/tasks/${taskId}/resolve-blocker`, { method: "POST" }),
  getExecutionState: (id: number) =>
    req<ExecutionState>(`/startups/${id}/execution/state`),

  // ---- Real App Generation in Isolated Workspace ----
  getGeneratedApp: (id: number) =>
    req<GeneratedApp>(`/startups/${id}/app`),
  generateApp: (id: number) =>
    req<GeneratedApp>(`/startups/${id}/app/generate`, { method: "POST" }),
  listGeneratedFiles: (id: number) =>
    req<GeneratedFileInfo[]>(`/startups/${id}/app/files`),
  getGeneratedFileContent: (id: number, path: string) =>
    req<GeneratedFileContent>(`/startups/${id}/app/files/content?path=${encodeURIComponent(path)}`),
  buildGeneratedApp: (id: number) =>
    req<GeneratedApp>(`/startups/${id}/app/build`, { method: "POST" }),
  startPreview: (id: number) =>
    req<GeneratedApp>(`/startups/${id}/app/preview/start`, { method: "POST" }),
  stopPreview: (id: number) =>
    req<GeneratedApp>(`/startups/${id}/app/preview/stop`, { method: "POST" }),

  // ---- Coworker Chat & Controlled App Modifications ----
  getCoworkerChat: (id: number) =>
    req<CoworkerChatMessage[]>(`/startups/${id}/app/chat`),
  sendCoworkerChatMessage: (id: number, message: string, preferredAgent?: string) =>
    req<CoworkerChatMessage>(`/startups/${id}/app/chat`, {
      method: "POST",
      body: JSON.stringify({ message, preferredAgent }),
    }),
  getAppChangeRequests: (id: number) =>
    req<AppChangeRequest[]>(`/startups/${id}/app/change-requests`),
  approveAppChangeRequest: (id: number, requestId: number) =>
    req<AppChangeRequest>(`/startups/${id}/app/change-requests/${requestId}/approve`, { method: "POST" }),
  rejectAppChangeRequest: (id: number, requestId: number) =>
    req<AppChangeRequest>(`/startups/${id}/app/change-requests/${requestId}/reject`, { method: "POST" }),
};

export const streamUrl = (id: number) => `${BASE}/startups/${id}/events/stream`;
