import { useState } from "react";
import { useSim } from "../../store/simulationStore";
import { DEPT, PRIORITY_COLOR } from "../../lib/theme";
import {
  isExecutionLifecycle,
  getPlanningTaskStats,
  getExecutionTaskStats,
} from "../../lib/lifecycle";
import type { TaskStatus, AgentType, ExecutionTask, AgentTask } from "../../types";

const STATUS_BADGE: Record<TaskStatus, { text: string; label: string }> = {
  PENDING: { text: "text-slate-400", label: "pending" },
  IN_PROGRESS: { text: "text-sky-300 font-medium", label: "in progress" },
  BLOCKED: { text: "text-rose-400 font-semibold", label: "blocked" },
  COMPLETED: { text: "text-emerald-400 font-medium", label: "completed" },
};

function getDeptTheme(dept: AgentType) {
  return (
    DEPT[dept] ?? {
      label: dept,
      accent: "text-slate-300",
      hex: "#94a3b8",
      glow: "",
      emoji: "📌",
      room: "",
    }
  );
}

export default function TasksSidebar() {
  const startup = useSim((s) => s.startup);
  const planningTasks = useSim((s) => s.tasks);
  const executionState = useSim((s) => s.executionState);

  const [selectedLifecycle, setSelectedLifecycle] = useState<"PLANNING" | "EXECUTION" | null>(null);

  const hasExecution = isExecutionLifecycle(startup, executionState);
  const activeLifecycle = selectedLifecycle ?? (hasExecution ? "EXECUTION" : "PLANNING");

  const planStats = getPlanningTaskStats(planningTasks);
  const executionTasks = executionState?.tasks ?? [];
  const execStats = getExecutionTaskStats(executionTasks);

  const currentStats = activeLifecycle === "EXECUTION" ? execStats : planStats;

  return (
    <aside className="w-72 shrink-0 glass border-r border-white/5 flex flex-col min-h-0 min-w-0 select-none">
      <div className="px-4 py-3 border-b border-white/5 shrink-0">
        <div className="flex items-center justify-between gap-2">
          <h2 className="text-sm font-bold text-white">Task Board</h2>
          <span
            data-testid="lifecycle-badge"
            className={`text-[10px] font-semibold px-2 py-0.5 rounded-full border ${
              activeLifecycle === "EXECUTION"
                ? "bg-emerald-500/10 text-emerald-300 border-emerald-500/30"
                : "bg-sky-500/10 text-sky-300 border-sky-500/30"
            }`}
          >
            {activeLifecycle === "EXECUTION" ? "Execution Tasks" : "Planning Tasks"}
          </span>
        </div>
        <p className="text-[11px] text-slate-400 mt-1" data-testid="task-count">
          {currentStats.completedCount}/{currentStats.totalCount} complete
        </p>

        {hasExecution && (
          <div className="flex rounded-lg bg-black/30 p-0.5 mt-2.5 border border-white/5">
            <button
              type="button"
              data-testid="tab-execution"
              onClick={() => setSelectedLifecycle("EXECUTION")}
              className={`flex-1 py-1 text-[10px] font-medium rounded-md transition ${
                activeLifecycle === "EXECUTION"
                  ? "bg-emerald-600/80 text-white shadow-sm"
                  : "text-slate-400 hover:text-slate-200"
              }`}
            >
              Execution ({execStats.completedCount}/{execStats.totalCount})
            </button>
            <button
              type="button"
              data-testid="tab-planning"
              onClick={() => setSelectedLifecycle("PLANNING")}
              className={`flex-1 py-1 text-[10px] font-medium rounded-md transition ${
                activeLifecycle === "PLANNING"
                  ? "bg-sky-600/80 text-white shadow-sm"
                  : "text-slate-400 hover:text-slate-200"
              }`}
            >
              Planning ({planStats.completedCount}/{planStats.totalCount})
            </button>
          </div>
        )}
      </div>

      <div className="flex-1 overflow-y-auto scroll-slim p-3 space-y-2">
        {activeLifecycle === "EXECUTION" ? (
          <>
            {hasExecution && !executionState ? (
              <div className="flex flex-col items-center justify-center p-6 text-center text-slate-400 space-y-2">
                <div className="w-5 h-5 border-2 border-emerald-400 border-t-transparent rounded-full animate-spin" />
                <span className="text-xs">Loading execution tasks…</span>
              </div>
            ) : executionTasks.length === 0 ? (
              <div className="p-4 text-center text-slate-500 text-xs space-y-1">
                <p className="text-slate-300 font-medium">Autonomous Execution Initialized</p>
                <p className="text-[11px] text-slate-500">Preparing roadmap tasks…</p>
              </div>
            ) : (
              executionTasks.map((task: ExecutionTask) => {
                const t = getDeptTheme(task.department);
                const isBlocked = task.status === "BLOCKED";
                const isCompleted = task.status === "COMPLETED";
                const progressBarColor = isCompleted
                  ? "#10b981"
                  : isBlocked
                  ? "#f43f5e"
                  : t.hex;

                const badge = STATUS_BADGE[task.status] ?? {
                  text: "text-slate-400",
                  label: task.status.toLowerCase(),
                };

                return (
                  <div
                    key={task.id}
                    data-testid={`execution-task-${task.id}`}
                    className={`rounded-lg bg-ink-900/70 border p-3 transition animate-fade-in-up ${
                      isBlocked
                        ? "border-rose-500/40 bg-rose-950/15"
                        : "border-white/5"
                    }`}
                  >
                    <div className="flex items-center justify-between gap-2">
                      <span className={`text-[10px] font-bold uppercase ${t.accent}`}>
                        {t.label}
                      </span>
                      <div className="flex items-center gap-1.5">
                        {task.priority && (
                          <span
                            className={`text-[9px] px-1.5 py-0.5 rounded border ${
                              PRIORITY_COLOR[task.priority] ?? "text-slate-400 border-slate-600"
                            }`}
                          >
                            {task.priority}
                          </span>
                        )}
                      </div>
                    </div>

                    <div className="text-sm font-medium text-slate-100 mt-1 leading-snug">
                      {task.title}
                    </div>

                    {task.assignedAgent && (
                      <div className="text-[10px] text-slate-400 mt-0.5">
                        Assigned: <span className="text-slate-300 font-medium">{task.assignedAgent}</span>
                      </div>
                    )}

                    <div className="mt-2 h-1.5 rounded-full bg-black/40 overflow-hidden">
                      <div
                        className="h-full rounded-full transition-all duration-500"
                        style={{
                          width: `${Math.max(0, Math.min(100, task.progress))}%`,
                          backgroundColor: progressBarColor,
                        }}
                      />
                    </div>

                    <div className="flex items-center justify-between mt-1">
                      <span className={`text-[10px] ${badge.text}`}>
                        {badge.label}
                      </span>
                      <span className="text-[10px] text-slate-400 font-mono">
                        {Math.round(task.progress)}%
                      </span>
                    </div>

                    {task.blockerReason && (
                      <div
                        data-testid={`blocker-reason-${task.id}`}
                        className="mt-2.5 p-2 rounded-md bg-rose-500/10 border border-rose-500/30 text-rose-300 text-[11px] leading-relaxed flex items-start gap-1.5"
                      >
                        <span className="shrink-0 text-xs">⚠️</span>
                        <div className="flex-1 min-w-0">
                          <span className="font-semibold text-rose-200">Blocker: </span>
                          <span>{task.blockerReason}</span>
                        </div>
                      </div>
                    )}
                  </div>
                );
              })
            )}
          </>
        ) : (
          <>
            {planningTasks.length === 0 && (
              <p className="text-xs text-slate-500 px-1 py-4 text-center">
                Tasks appear as the team gets to work…
              </p>
            )}
            {planningTasks.map((task: AgentTask) => {
              const t = getDeptTheme(task.agentType);
              const badge = STATUS_BADGE[task.status] ?? {
                text: "text-slate-400",
                label: task.status.toLowerCase(),
              };
              return (
                <div
                  key={task.id}
                  data-testid={`planning-task-${task.id}`}
                  className="rounded-lg bg-ink-900/70 border border-white/5 p-3 animate-fade-in-up"
                >
                  <div className="flex items-center justify-between gap-2">
                    <span className={`text-[10px] font-bold uppercase ${t.accent}`}>
                      {t.label}
                    </span>
                    <span
                      className={`text-[9px] px-1.5 py-0.5 rounded border ${
                        PRIORITY_COLOR[task.priority] ?? "text-slate-400 border-slate-600"
                      }`}
                    >
                      {task.priority}
                    </span>
                  </div>
                  <div className="text-sm font-medium text-slate-100 mt-1">{task.title}</div>
                  <div className="mt-2 h-1.5 rounded-full bg-black/40 overflow-hidden">
                    <div
                      className="h-full rounded-full transition-all duration-500"
                      style={{
                        width: `${Math.max(0, Math.min(100, task.progress))}%`,
                        backgroundColor: t.hex,
                      }}
                    />
                  </div>
                  <div className="flex items-center justify-between mt-1">
                    <span className={`text-[10px] ${badge.text}`}>
                      {badge.label}
                    </span>
                    <span className="text-[10px] text-slate-500 font-mono">{task.progress}%</span>
                  </div>
                </div>
              );
            })}
          </>
        )}
      </div>
    </aside>
  );
}
