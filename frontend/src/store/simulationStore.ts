import { create } from "zustand";
import { api } from "../services/api";
import type {
  Agent,
  AgentMessage,
  AgentTask,
  Blueprint,
  Debate,
  ExecutionState,
  Plans,
  Startup,
  StartupEvent,
  ExecutionTraceEntry,
  GeneratedApp,
  GeneratedFileInfo,
  GeneratedFileContent,
} from "../types";

interface SimState {
  startup: Startup | null;
  agents: Agent[];
  tasks: AgentTask[];
  messages: AgentMessage[];
  debates: Debate[];
  events: StartupEvent[];
  blueprint: Blueprint | null;
  plans: Plans | null;
  executionState: ExecutionState | null;
  trace: ExecutionTraceEntry[];
  generatedApp: GeneratedApp | null;
  generatedFiles: GeneratedFileInfo[];
  selectedFileContent: GeneratedFileContent | null;
  loading: boolean;
  error: string | null;

  create: (idea: string, name?: string) => Promise<Startup>;
  load: (id: number) => Promise<void>;
  startSimulation: (speed?: number) => Promise<void>;
  retryCeoAnalysis: () => Promise<void>;
  retryDepartmentAnalysis: (dept: "developer" | "marketing" | "finance") => Promise<void>;
  onEvent: (event: StartupEvent) => void;
  refresh: () => Promise<void>;
  loadBlueprint: () => Promise<void>;
  loadPlans: () => Promise<void>;
  loadTrace: (agent?: string) => Promise<void>;
  loadExecutionState: () => Promise<void>;
  startExecution: () => Promise<void>;
  pauseExecution: () => Promise<void>;
  resumeExecution: () => Promise<void>;
  stepExecution: () => Promise<void>;
  resolveTaskBlocker: (taskId: number) => Promise<void>;
  loadGeneratedApp: () => Promise<void>;
  generateApp: () => Promise<void>;
  loadGeneratedFiles: () => Promise<void>;
  selectGeneratedFile: (path: string) => Promise<void>;
  buildGeneratedApp: () => Promise<void>;
  startPreview: () => Promise<void>;
  stopPreview: () => Promise<void>;
  reset: () => void;
}

export const useSim = create<SimState>((set, get) => ({
  startup: null,
  agents: [],
  tasks: [],
  messages: [],
  debates: [],
  events: [],
  blueprint: null,
  plans: null,
  executionState: null,
  trace: [],
  generatedApp: null,
  generatedFiles: [],
  selectedFileContent: null,
  loading: false,
  error: null,

  async create(idea, name) {
    set({ loading: true, error: null });
    try {
      const startup = await api.createStartup(idea, name);
      set({ startup, loading: false });
      await get().load(startup.id);
      return startup;
    } catch (e) {
      set({ loading: false, error: (e as Error).message });
      throw e;
    }
  },

  async load(id) {
    const [startup, agents, tasks, messages, debates, events, plans, executionState, blueprint, trace, generatedApp] = await Promise.all([
      api.getStartup(id),
      api.agents(id),
      api.tasks(id),
      api.messages(id),
      api.debates(id),
      api.events(id),
      api.plans(id),
      api.getExecutionState(id).catch(() => null),
      api.blueprint(id).catch(() => null),
      api.trace(id).catch(() => []),
      api.getGeneratedApp(id).catch(() => null),
    ]);
    set({ startup, agents, tasks, messages, debates, events, plans, executionState, blueprint, trace, generatedApp });
  },

  async startSimulation(speed = 1.0) {
    const s = get().startup;
    if (!s) return;
    await api.simulate(s.id, speed);
  },

  async retryCeoAnalysis() {
    const s = get().startup;
    if (!s) return;
    await api.retryCeoAnalysis(s.id);
    void get().refresh();
  },

  async retryDepartmentAnalysis(dept) {
    const s = get().startup;
    if (!s) return;
    await api.retryDepartmentAnalysis(s.id, dept);
    void get().refresh();
  },

  onEvent(event) {
    set((st) => ({ events: [...st.events, event] }));
    void get().refresh();
    if (event.type === "BLUEPRINT_GENERATED") void get().loadBlueprint();
    if (event.type.startsWith("EXECUTION_") || event.type.startsWith("TASK_") || event.type === "METRIC_CHANGED") {
      void get().loadExecutionState();
    }
  },

  async refresh() {
    const s = get().startup;
    if (!s) return;
    const [startup, agents, tasks, messages, debates, plans, executionState] = await Promise.all([
      api.getStartup(s.id),
      api.agents(s.id),
      api.tasks(s.id),
      api.messages(s.id),
      api.debates(s.id),
      api.plans(s.id),
      api.getExecutionState(s.id).catch(() => get().executionState),
    ]);
    set({ startup, agents, tasks, messages, debates, plans, executionState });
  },

  async loadBlueprint() {
    const s = get().startup;
    if (!s) return;
    try {
      set({ blueprint: await api.blueprint(s.id) });
    } catch {
      /* blueprint not ready yet */
    }
  },

  async loadPlans() {
    const s = get().startup;
    if (!s) return;
    try {
      set({ plans: await api.plans(s.id) });
    } catch {
      /* plans not ready yet */
    }
  },

  async loadTrace(agent?: string) {
    const s = get().startup;
    if (!s) return;
    try {
      set({ trace: await api.trace(s.id, agent) });
    } catch {
      /* trace not available */
    }
  },

  async loadExecutionState() {
    const s = get().startup;
    if (!s) return;
    try {
      const executionState = await api.getExecutionState(s.id);
      set({ executionState });
    } catch {
      /* execution state not initialized yet */
    }
  },

  async startExecution() {
    const s = get().startup;
    if (!s) return;
    const executionState = await api.startExecution(s.id);
    set({ executionState });
    void get().refresh();
  },

  async pauseExecution() {
    const s = get().startup;
    if (!s) return;
    const executionState = await api.pauseExecution(s.id);
    set({ executionState });
  },

  async resumeExecution() {
    const s = get().startup;
    if (!s) return;
    const executionState = await api.resumeExecution(s.id);
    set({ executionState });
  },

  async stepExecution() {
    const s = get().startup;
    if (!s) return;
    const executionState = await api.stepExecution(s.id);
    set({ executionState });
    void get().refresh();
  },

  async resolveTaskBlocker(taskId: number) {
    const s = get().startup;
    if (!s) return;
    const executionState = await api.resolveTaskBlocker(s.id, taskId);
    set({ executionState });
    void get().refresh();
  },

  async loadGeneratedApp() {
    const s = get().startup;
    if (!s) return;
    try {
      const generatedApp = await api.getGeneratedApp(s.id);
      set({ generatedApp });
    } catch {
      /* app not generated yet */
    }
  },

  async generateApp() {
    const s = get().startup;
    if (!s) return;
    const generatedApp = await api.generateApp(s.id);
    set({ generatedApp });
    await get().loadGeneratedFiles();
  },

  async loadGeneratedFiles() {
    const s = get().startup;
    if (!s) return;
    try {
      const generatedFiles = await api.listGeneratedFiles(s.id);
      set({ generatedFiles });
      if (generatedFiles.length > 0 && !get().selectedFileContent) {
        // Automatically select src/App.tsx or package.json
        const defaultFile = generatedFiles.find((f) => f.path === "src/App.tsx") ||
                            generatedFiles.find((f) => !f.isDirectory);
        if (defaultFile) {
          void get().selectGeneratedFile(defaultFile.path);
        }
      }
    } catch {
      /* files not available */
    }
  },

  async selectGeneratedFile(path: string) {
    const s = get().startup;
    if (!s) return;
    try {
      const selectedFileContent = await api.getGeneratedFileContent(s.id, path);
      set({ selectedFileContent });
    } catch {
      /* could not read file */
    }
  },

  async buildGeneratedApp() {
    const s = get().startup;
    if (!s) return;
    const generatedApp = await api.buildGeneratedApp(s.id);
    set({ generatedApp });
    await get().loadGeneratedFiles();
  },

  async startPreview() {
    const s = get().startup;
    if (!s) return;
    const generatedApp = await api.startPreview(s.id);
    set({ generatedApp });
  },

  async stopPreview() {
    const s = get().startup;
    if (!s) return;
    const generatedApp = await api.stopPreview(s.id);
    set({ generatedApp });
  },

  reset() {
    set({
      startup: null,
      agents: [],
      tasks: [],
      messages: [],
      debates: [],
      events: [],
      blueprint: null,
      plans: null,
      executionState: null,
      trace: [],
      generatedApp: null,
      generatedFiles: [],
      selectedFileContent: null,
      error: null,
    });
  },
}));
