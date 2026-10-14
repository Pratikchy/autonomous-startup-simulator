import { useRef } from "react";
import { useSim } from "../../store/simulationStore";
import { DEPT } from "../../lib/theme";

const ROUND_LABEL: Record<number, string> = {
  1: "Round 1 · Positions",
  2: "Round 2 · Challenges",
  3: "Round 3 · Convergence",
};

// Short badge per debate turn type; the CEO's framing/synthesis get their own.
const TYPE_BADGE: Record<string, string> = {
  FRAMING: "Framing",
  POSITION: "Position",
  CHALLENGE: "Challenge",
  CONVERGENCE: "Revised",
  SYNTHESIS: "Synthesis",
};

export default function BoardroomView({ onClose }: { onClose: () => void }) {
  const debates = useSim((s) => s.debates);
  const debate = debates[debates.length - 1];
  const scrollRef = useRef<HTMLDivElement>(null);

  const warnings = debate?.status === "COMPLETED_WITH_WARNINGS";

  return (
    <div className="absolute inset-0 z-40 grid place-items-center bg-black/60 backdrop-blur-sm p-4 sm:p-6">
      <div className="glass w-full max-w-3xl max-h-[85vh] rounded-2xl flex flex-col min-h-0 min-w-0 shadow-2xl">
        {/* Fixed header */}
        <div className="shrink-0 px-5 py-4 border-b border-white/5 flex items-start justify-between gap-3">
          <div className="min-w-0">
            <div className="flex items-center gap-2 flex-wrap">
              <span className="text-xl">⚖️</span>
              <h2 className="font-bold text-white break-words">
                {debate ? debate.topic : "Boardroom"}
              </h2>
              {warnings && (
                <span className="text-[10px] px-2 py-0.5 rounded-full bg-amber-500/15 text-amber-300 border border-amber-500/30 whitespace-nowrap">
                  ⚠️ completed with warnings
                </span>
              )}
            </div>
            {debate && (
              <p className="text-[12px] text-slate-400 mt-1 break-words">{debate.question}</p>
            )}
          </div>
          <button
            onClick={onClose}
            aria-label="Close"
            className="shrink-0 text-slate-400 hover:text-white text-xl leading-none"
          >
            ×
          </button>
        </div>

        {/* Single scrollable content region: rounds + CEO decision together */}
        <div ref={scrollRef} className="flex-1 min-h-0 overflow-y-auto overscroll-contain scroll-slim p-5">
          {!debate && (
            <p className="text-sm text-slate-500">
              The boardroom convenes once the analysis phase completes…
            </p>
          )}

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-3 items-start">
            {debate?.messages.map((m, i) => {
              const t = DEPT[m.agentType];
              const prev = debate.messages[i - 1];
              const showRound =
                m.debateRound != null &&
                m.debateRound !== (prev?.debateRound ?? null) &&
                m.messageType !== "SYNTHESIS";
              const badge = m.messageType ? TYPE_BADGE[m.messageType] : null;
              return (
                <div key={m.id} className="contents">
                  {showRound && (
                    <div className="lg:col-span-2 flex items-center gap-2 mt-1 mb-1">
                      <div className="h-px flex-1 bg-white/10" />
                      <span className="text-[10px] uppercase tracking-wider text-slate-500 whitespace-nowrap">
                        {ROUND_LABEL[m.debateRound as number] ?? `Round ${m.debateRound}`}
                      </span>
                      <div className="h-px flex-1 bg-white/10" />
                    </div>
                  )}
                  <div className="flex gap-3 min-w-0 animate-fade-in-up">
                    <div
                      className="shrink-0 h-9 w-9 rounded-full grid place-items-center text-sm border"
                      style={{ borderColor: t.hex, color: t.hex }}
                    >
                      {t.emoji}
                    </div>
                    <div className="min-w-0 flex-1 rounded-2xl rounded-tl-sm bg-ink-900/80 border border-white/5 px-3.5 py-2">
                      <div className="flex items-center gap-2 flex-wrap">
                        <span className={`text-[11px] font-bold ${t.accent}`}>
                          {m.agentLabel}
                        </span>
                        {badge && (
                          <span className="text-[9px] px-1.5 py-0.5 rounded bg-white/5 text-slate-400 uppercase tracking-wide">
                            {badge}
                            {m.targetAgent ? ` → ${DEPT[m.targetAgent].label}` : ""}
                          </span>
                        )}
                      </div>
                      <div className="text-sm text-slate-200 mt-0.5 whitespace-pre-line break-words">
                        {m.content}
                      </div>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>

          {debate?.decision && (
            <div className="mt-4 rounded-xl bg-ceo/10 border border-ceo/40 p-4">
              <div className="flex items-center gap-2 text-ceo font-bold text-sm">
                ✅ Decision — {debate.decision.decidedBy}
              </div>
              <div className="text-sm text-white mt-1 break-words">{debate.decision.decision}</div>
              <div className="text-[12px] text-slate-400 mt-1 break-words">
                {debate.decision.reason}
              </div>
              {debate.decision.finalMvpDirection && (
                <div className="mt-2 text-[12px]">
                  <span className="text-slate-500">What we build: </span>
                  <span className="text-slate-200 break-words">
                    {debate.decision.finalMvpDirection}
                  </span>
                </div>
              )}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-4">
                <SynthesisList
                  label="Accepted"
                  items={debate.decision.acceptedArguments}
                  tone="text-emerald-300"
                />
                <SynthesisList
                  label="Rejected"
                  items={debate.decision.rejectedArguments}
                  tone="text-rose-300"
                />
                <SynthesisList
                  label="Priorities"
                  items={debate.decision.finalPriorities}
                  tone="text-sky-300"
                />
                <SynthesisList
                  label="Risks"
                  items={debate.decision.finalRisks}
                  tone="text-amber-300"
                />
              </div>
              {debate.decision.affectedDepartments.length > 0 && (
                <div className="flex gap-1.5 mt-2 flex-wrap">
                  {debate.decision.affectedDepartments.map((d) => (
                    <span
                      key={d}
                      className="text-[10px] px-2 py-0.5 rounded-full bg-white/5 text-slate-300"
                    >
                      {d}
                    </span>
                  ))}
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function SynthesisList({
  label,
  items,
  tone,
}: {
  label: string;
  items: string[];
  tone: string;
}) {
  if (!items || items.length === 0) return null;
  return (
    <div className="mt-2 min-w-0">
      <div className={`text-[10px] uppercase tracking-wider font-bold ${tone}`}>
        {label}
      </div>
      <ul className="mt-0.5 space-y-0.5">
        {items.map((it, i) => (
          <li key={i} className="text-[12px] text-slate-300 flex gap-1.5">
            <span className="text-slate-600">•</span>
            <span className="break-words min-w-0">{it}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}
