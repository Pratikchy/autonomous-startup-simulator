import { useSim } from "../../store/simulationStore";
import { DEPT, PRIORITY_COLOR } from "../../lib/theme";
import type { TaskStatus } from "../../types";

const STATUS_BADGE: Record<TaskStatus, string> = {
  PENDING: "text-slate-400",
  IN_PROGRESS: "text-sky-300",
  BLOCKED: "text-rose-300",
  COMPLETED: "text-emerald-400",
};

export default function TasksSidebar() {
  const tasks = useSim((s) => s.tasks);

  return (
    <aside className="w-72 shrink-0 glass border-r border-white/5 flex flex-col min-h-0 min-w-0">
      <div className="px-4 py-3 border-b border-white/5 shrink-0">
        <h2 className="text-sm font-bold text-white">Task Board</h2>
        <p className="text-[11px] text-slate-400">
          {tasks.filter((t) => t.status === "COMPLETED").length}/{tasks.length}{" "}
          complete
        </p>
      </div>
      <div className="flex-1 overflow-y-auto scroll-slim p-3 space-y-2">
        {tasks.length === 0 && (
          <p className="text-xs text-slate-500 px-1">
            Tasks appear as the team gets to work…
          </p>
        )}
        {tasks.map((task) => {
          const t = DEPT[task.agentType];
          return (
            <div
              key={task.id}
              className="rounded-lg bg-ink-900/70 border border-white/5 p-3 animate-fade-in-up"
            >
              <div className="flex items-center justify-between gap-2">
                <span className={`text-[10px] font-bold uppercase ${t.accent}`}>
                  {t.label}
                </span>
                <span
                  className={`text-[9px] px-1.5 py-0.5 rounded border ${
                    PRIORITY_COLOR[task.priority]
                  }`}
                >
                  {task.priority}
                </span>
              </div>
              <div className="text-sm text-slate-100 mt-1">{task.title}</div>
              <div className="mt-2 h-1.5 rounded-full bg-black/40 overflow-hidden">
                <div
                  className="h-full rounded-full transition-all duration-500"
                  style={{ width: `${task.progress}%`, backgroundColor: t.hex }}
                />
              </div>
              <div className="flex items-center justify-between mt-1">
                <span className={`text-[10px] ${STATUS_BADGE[task.status]}`}>
                  {task.status.replace("_", " ").toLowerCase()}
                </span>
                <span className="text-[10px] text-slate-500">{task.progress}%</span>
              </div>
            </div>
          );
        })}
      </div>
    </aside>
  );
}
