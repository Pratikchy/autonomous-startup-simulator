import type { AgentType } from "../types";
import { useSim } from "../store/simulationStore";
import { DEPT, STATE_LABEL } from "../lib/theme";
import { useState } from "react";

export default function DepartmentDetailPanel({
  type,
  onClose,
  onOpenWorkHistory,
}: {
  type: AgentType;
  onClose: () => void;
  onOpenWorkHistory?: (type: AgentType) => void;
}) {
  const agents = useSim((s) => s.agents);
  const tasks = useSim((s) => s.tasks);
  const messages = useSim((s) => s.messages);
  const startup = useSim((s) => s.startup);
  const plans = useSim((s) => s.plans);
  const retryCeoAnalysis = useSim((s) => s.retryCeoAnalysis);
  const retryDepartmentAnalysis = useSim((s) => s.retryDepartmentAnalysis);
  const [retrying, setRetrying] = useState(false);

  const t = DEPT[type];
  const agent = agents.find((a) => a.type === type);
  const deptTasks = tasks.filter((x) => x.agentType === type);
  const deptMsgs = messages.filter((m) => m.agentType === type);

  // The CEO department surfaces the real (Phase 2A) strategic analysis produced
  // by the LLM (or the deterministic fallback), straight from the backend state.
  const isCeo = type === "CEO";
  const hasCeoAnalysis =
    isCeo &&
    !!startup &&
    (!!startup.executiveSummary ||
      !!startup.problem ||
      startup.strategicObjectives.length > 0);

  // Phase 2B: the department analysis (technical / marketing / budget) with its
  // provider + failure metadata, and the endpoint slug used for per-dept retry.
  const deptSlug =
    type === "DEVELOPMENT"
      ? "developer"
      : type === "MARKETING"
        ? "marketing"
        : type === "FINANCE"
          ? "finance"
          : null;

  const currentPlan =
    type === "DEVELOPMENT"
      ? plans?.technical
      : type === "MARKETING"
        ? plans?.marketing
        : type === "FINANCE"
          ? plans?.budget
          : null;

  const analysisMeta = currentPlan
    ? {
        provider: currentPlan.analysisProvider,
        failed: currentPlan.analysisFailed,
        error: currentPlan.analysisError,
      }
    : null;

  async function onRetry() {
    setRetrying(true);
    try {
      if (isCeo) {
        await retryCeoAnalysis();
      } else if (deptSlug) {
        await retryDepartmentAnalysis(deptSlug);
      }
    } finally {
      setRetrying(false);
    }
  }

  return (
    <div className="absolute inset-y-0 right-0 z-30 w-full max-w-md glass border-l border-white/10 shadow-2xl flex flex-col animate-fade-in-up">
      <div className={`px-5 py-4 border-b border-white/5 bg-gradient-to-br ${t.room}`}>
        <div className="flex items-start justify-between">
          <div className="flex items-center gap-3">
            <span className="text-3xl">{t.emoji}</span>
            <div>
              <h2 className={`font-bold ${t.accent}`}>{t.label}</h2>
              {agent && (
                <p className="text-sm text-white">
                  {agent.name} ·{" "}
                  <span className="text-slate-300">{STATE_LABEL[agent.state]}</span>
                </p>
              )}
            </div>
          </div>
          <button
            onClick={onClose}
            className="text-slate-300 hover:text-white text-2xl leading-none"
          >
            ×
          </button>
        </div>
        {agent && (
          <p className="text-[12px] text-slate-300 mt-2">{agent.mandate}</p>
        )}
        {onOpenWorkHistory && (
          <button
            onClick={() => onOpenWorkHistory(type)}
            className="mt-3 w-full py-1.5 px-3 rounded-lg text-xs font-semibold bg-white/10 hover:bg-white/20 text-white border border-white/15 flex items-center justify-center gap-1.5 transition shadow-sm"
          >
            <span>📜</span>
            <span>View {t.label} Work History</span>
          </button>
        )}
      </div>

      <div className="flex-1 overflow-y-auto scroll-slim p-5 space-y-5">
        {agent?.currentActivity && (
          <div className="rounded-lg bg-ink-900/70 border border-white/5 px-3 py-2 text-sm text-slate-200">
            <span className="text-slate-500 text-[11px] uppercase mr-2">Now</span>
            {agent.currentActivity}
          </div>
        )}

        {isCeo && startup && (
          <section className="space-y-3">
            <div className="flex items-center justify-between">
              <h3 className="text-xs font-bold uppercase text-slate-400">
                Strategic Analysis
              </h3>
              {startup.ceoAnalysisProvider && (
                <span className="text-[10px] uppercase tracking-wide text-slate-500">
                  via {startup.ceoAnalysisProvider}
                </span>
              )}
            </div>

            {startup.ceoAnalysisFailed && (
              <div className="rounded-lg border border-amber-500/40 bg-amber-500/10 px-3 py-2">
                <p className="text-[12px] text-amber-200">
                  The live analysis didn&apos;t complete
                  {startup.ceoAnalysisError ? ` (${startup.ceoAnalysisError})` : ""} — a
                  fallback strategy is shown below.
                </p>
              </div>
            )}

            {isCeo && (
              <button
                onClick={onRetry}
                disabled={retrying}
                className="text-[12px] rounded-md px-3 py-1.5 font-medium text-white disabled:opacity-50"
                style={{ backgroundColor: t.hex }}
              >
                {retrying ? "Re-running analysis…" : "Re-run CEO analysis"}
              </button>
            )}

            {hasCeoAnalysis ? (
              <div className="space-y-3">
                <Field label="Executive Summary" value={startup.executiveSummary} />
                <Field label="Problem" value={startup.problem} />
                <Field label="Target Customer" value={startup.targetAudience} />
                <Field label="Value Proposition" value={startup.valueProposition} />
                <Field label="Business Model" value={startup.businessModel} />
                <Field label="MVP Direction" value={startup.mvpDirection} />
                <ListField
                  label="Strategic Priorities"
                  items={startup.strategicObjectives}
                />
                <ListField label="Key Assumptions" items={startup.assumptions} />
              </div>
            ) : (
              <p className="text-xs text-slate-500">
                The CEO hasn&apos;t produced an analysis yet.
              </p>
            )}
          </section>
        )}

        {deptSlug && (
          <section className="space-y-3">
            <div className="flex items-center justify-between">
              <h3 className="text-xs font-bold uppercase text-slate-400">
                Department Analysis
              </h3>
              {analysisMeta?.provider && (
                <span className="text-[10px] uppercase tracking-wide text-slate-500">
                  via {analysisMeta.provider}
                </span>
              )}
            </div>

            {analysisMeta?.failed && (
              <div className="rounded-lg border border-amber-500/40 bg-amber-500/10 px-3 py-2">
                <p className="text-[12px] text-amber-200">
                  The live analysis didn&apos;t complete
                  {analysisMeta.error ? ` (${analysisMeta.error})` : ""} — a
                  fallback plan is shown below.
                </p>
              </div>
            )}

            <button
              onClick={onRetry}
              disabled={retrying}
              className="text-[12px] rounded-md px-3 py-1.5 font-medium text-white disabled:opacity-50"
              style={{ backgroundColor: t.hex }}
            >
              {retrying ? "Re-running analysis…" : `Re-run ${t.label} analysis`}
            </button>

            {type === "DEVELOPMENT" && plans?.technical && (
              <div className="space-y-3">
                <Field label="Solution" value={startup?.solution ?? null} />
                <Field label="Architecture" value={plans.technical.architecture} />
                <Field label="Tech Stack" value={plans.technical.techStack} />
                <Field label="Timeline" value={plans.technical.timeline} />
                <Field label="Technical Risks" value={plans.technical.technicalRisks} />
                <Field
                  label="Est. Engineering Months"
                  value={
                    plans.technical.estimatedEngineeringMonths > 0
                      ? String(plans.technical.estimatedEngineeringMonths)
                      : null
                  }
                />
              </div>
            )}

            {type === "MARKETING" && plans?.marketing && (
              <div className="space-y-3">
                <Field label="Positioning" value={plans.marketing.positioning} />
                <Field
                  label="Competitor Analysis"
                  value={plans.marketing.competitorAnalysis}
                />
                <Field label="Pricing Strategy" value={plans.marketing.pricingStrategy} />
                <Field label="Channels" value={plans.marketing.channels} />
                <Field label="Go-to-Market" value={plans.marketing.goToMarket} />
              </div>
            )}

            {type === "FINANCE" && plans?.budget && (
              <div className="space-y-3">
                <Field
                  label="Development Cost"
                  value={money(plans.budget.developmentCost)}
                />
                <Field
                  label="Marketing Budget"
                  value={money(plans.budget.marketingBudget)}
                />
                <Field label="Monthly Burn" value={money(plans.budget.monthlyBurn)} />
                <Field
                  label="Projected Monthly Revenue"
                  value={money(plans.budget.projectedMonthlyRevenue)}
                />
                <Field
                  label="Runway"
                  value={
                    plans.budget.runwayMonths > 0
                      ? `${plans.budget.runwayMonths} months`
                      : null
                  }
                />
                <Field
                  label="Break-even Assumption"
                  value={plans.budget.breakEvenAssumption}
                />
              </div>
            )}

            {!currentPlan && (
              <p className="text-xs text-slate-500">
                No analysis produced yet.
              </p>
            )}
          </section>
        )}

        <section>
          <h3 className="text-xs font-bold uppercase text-slate-400 mb-2">Tasks</h3>
          <div className="space-y-2">
            {deptTasks.length === 0 && (
              <p className="text-xs text-slate-500">No tasks yet.</p>
            )}
            {deptTasks.map((task) => (
              <div
                key={task.id}
                className="rounded-lg bg-ink-900/70 border border-white/5 p-3"
              >
                <div className="text-sm text-white">{task.title}</div>
                {task.description && (
                  <p className="text-[12px] text-slate-400 mt-0.5">
                    {task.description}
                  </p>
                )}
                <div className="mt-2 h-1.5 rounded-full bg-black/40 overflow-hidden">
                  <div
                    className="h-full rounded-full transition-all"
                    style={{ width: `${task.progress}%`, backgroundColor: t.hex }}
                  />
                </div>
              </div>
            ))}
          </div>
        </section>

        <section>
          <h3 className="text-xs font-bold uppercase text-slate-400 mb-2">
            Messages
          </h3>
          <div className="space-y-2">
            {deptMsgs.length === 0 && (
              <p className="text-xs text-slate-500">Nothing said yet.</p>
            )}
            {deptMsgs.map((m) => (
              <div
                key={m.id}
                className="rounded-lg bg-ink-900/70 border border-white/5 px-3 py-2 text-sm text-slate-200"
              >
                {m.content}
                {m.debateId != null && (
                  <span className="ml-2 text-[10px] text-ceo">· boardroom</span>
                )}
              </div>
            ))}
          </div>
        </section>
      </div>
    </div>
  );
}

function money(v: number): string | null {
  if (v == null || Number.isNaN(v)) return null;
  return `$${Math.round(v).toLocaleString()}`;
}

function Field({ label, value }: { label: string; value: string | null }) {
  if (!value) return null;
  return (
    <div>
      <div className="text-[11px] font-bold uppercase text-slate-500 mb-0.5">
        {label}
      </div>
      <p className="text-sm text-slate-200 whitespace-pre-line">{value}</p>
    </div>
  );
}

function ListField({ label, items }: { label: string; items: string[] }) {
  if (!items || items.length === 0) return null;
  return (
    <div>
      <div className="text-[11px] font-bold uppercase text-slate-500 mb-1">
        {label}
      </div>
      <ul className="space-y-1">
        {items.map((it, i) => (
          <li key={i} className="text-sm text-slate-200 flex gap-2">
            <span className="text-slate-500">•</span>
            <span>{it}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}
