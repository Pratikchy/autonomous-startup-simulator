import { useSim } from "../../store/simulationStore";
import { money } from "../../lib/theme";
import type { ExecutionState, SimulationPhase } from "../../types";

function Meter({ label, value, hex }: { label: string; value: number; hex: string }) {
  return (
    <div>
      <div className="flex justify-between text-[11px] mb-1">
        <span className="text-slate-400">{label}</span>
        <span className="text-slate-200 font-medium">{value}</span>
      </div>
      <div className="h-1.5 rounded-full bg-black/40 overflow-hidden">
        <div
          className="h-full rounded-full transition-all duration-700"
          style={{ width: `${value}%`, backgroundColor: hex }}
        />
      </div>
    </div>
  );
}

// Progress across the planning journey the user actually watches (the pipeline
// Idea → Analysis → Debate → Decision → Plan → Completed). Used before the
// optional execution phase begins, so "Overall progress" reflects real
// completed planning work instead of the execution-only metric, which is 0
// until the 90-day build starts.
const PHASE_PROGRESS: Record<SimulationPhase, number> = {
  IDEA: 10,
  ANALYSIS: 30,
  DEBATE: 50,
  DECISION: 70,
  PLAN: 90,
  COMPLETED: 100,
  EXECUTION: 100,
};

// Execution build metrics are authoritative only once the execution phase has
// produced a real signal; before that they are structurally 0 (no execution
// tasks yet) and must not be shown as the startup's headline progress.
function executionActive(ex: ExecutionState | null): boolean {
  if (!ex) return false;
  return (
    ex.isRunning ||
    ex.isCompleted ||
    ex.overallProgress > 0 ||
    ex.mvpProgress > 0 ||
    (ex.tasks?.length ?? 0) > 0
  );
}

export default function HealthSidebar() {
  const startup = useSim((s) => s.startup);
  const executionState = useSim((s) => s.executionState);
  if (!startup) return null;
  const h = startup.health;

  const execActive = executionActive(executionState);

  // Overall progress: real execution build progress once execution is live,
  // otherwise the planning-pipeline completion for the phase actually reached.
  const overallProgress = execActive
    ? executionState!.overallProgress
    : PHASE_PROGRESS[startup.currentPhase] ?? h.overallProgress;

  // MVP progress is a build metric. Before the build starts there is no real
  // MVP progress to show — surface a clear "not started" state rather than a
  // misleading 0%.
  const mvpProgress = execActive ? executionState!.mvpProgress : null;

  const technicalFeasibility = h.technicalFeasibility;
  const marketReadiness = executionState?.marketReadiness ?? h.marketReadiness;
  const financialHealth = executionState?.financialHealth ?? h.financialHealth;
  const runwayMonths = executionState?.runwayMonths ?? h.runwayMonths;
  const budgetRemaining = executionState?.budgetRemaining;

  return (
    <div className="glass border border-white/5 rounded-xl p-4">
      <h2 className="text-sm font-bold text-white mb-3">Startup Health</h2>

      <div className="mb-4">
        <div className="flex items-end justify-between">
          <span className="text-[11px] text-slate-400">Overall progress</span>
          <span className="text-2xl font-extrabold text-white leading-none">
            {overallProgress}
            <span className="text-sm text-slate-500">%</span>
          </span>
        </div>
        <div className="h-2 mt-1.5 rounded-full bg-black/40 overflow-hidden">
          <div
            className="h-full rounded-full bg-gradient-to-r from-sky-500 to-emerald-400 transition-all duration-700"
            style={{ width: `${overallProgress}%` }}
          />
        </div>
        {!execActive && (
          <p className="text-[10px] text-slate-500 mt-1">Planning phase</p>
        )}
      </div>

      <div className="space-y-2.5">
        {mvpProgress == null ? (
          <div>
            <div className="flex justify-between text-[11px] mb-1">
              <span className="text-slate-400">MVP progress</span>
              <span className="text-slate-500 italic">Not started</span>
            </div>
            <div className="h-1.5 rounded-full bg-black/40 overflow-hidden">
              <div className="h-full w-0 rounded-full" />
            </div>
          </div>
        ) : (
          <Meter label="MVP progress" value={mvpProgress} hex="#38bdf8" />
        )}
        <Meter label="Technical feasibility" value={technicalFeasibility} hex="#818cf8" />
        <Meter label="Market readiness" value={marketReadiness} hex="#f472b6" />
        <Meter label="Financial health" value={financialHealth} hex="#34d399" />
      </div>

      <div className="grid grid-cols-2 gap-2 mt-4">
        <div className="rounded-lg bg-ink-900/70 border border-white/5 p-2.5">
          <div className="text-[10px] text-slate-400">Budget left</div>
          <div className="text-lg font-bold text-emerald-300">
            {budgetRemaining == null ? (
              <span className="text-slate-500">—</span>
            ) : (
              money(budgetRemaining)
            )}
          </div>
        </div>
        <div className="rounded-lg bg-ink-900/70 border border-white/5 p-2.5">
          <div className="text-[10px] text-slate-400">Runway</div>
          <div className="text-lg font-bold text-sky-300">
            {runwayMonths.toFixed(1)}
            <span className="text-xs text-slate-500"> mo</span>
          </div>
        </div>
      </div>
    </div>
  );
}
