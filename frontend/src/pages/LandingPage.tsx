import { useEffect, useState } from "react";
import { useSim } from "../store/simulationStore";
import { api } from "../services/api";
import type { Startup } from "../types";

const EXAMPLES = [
  "An AI-powered app that scans skincare products and recommends a routine",
  "A marketplace connecting local farmers directly with restaurants",
  "A tool that turns meeting recordings into action items and follow-ups",
];

export default function LandingPage({ onReady }: { onReady: (id: number) => void }) {
  const create = useSim((s) => s.create);
  const load = useSim((s) => s.load);
  const startSimulation = useSim((s) => s.startSimulation);
  const loading = useSim((s) => s.loading);
  const error = useSim((s) => s.error);

  const [idea, setIdea] = useState("");
  const [name, setName] = useState("");
  const [busy, setBusy] = useState(false);
  const [lookupId, setLookupId] = useState("");

  // Saved startups list
  const [savedStartups, setSavedStartups] = useState<Startup[]>([]);
  const [loadingSaved, setLoadingSaved] = useState(true);

  const handleOpenById = (e: React.FormEvent) => {
    e.preventDefault();
    const idNum = parseInt(lookupId.trim(), 10);
    if (isNaN(idNum) || idNum <= 0) return;
    onReady(idNum);
  };

  useEffect(() => {
    api.listStartups()
      .then(setSavedStartups)
      .catch(() => {/* ignore if backend not up */})
      .finally(() => setLoadingSaved(false));
  }, []);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (idea.trim().length < 8 || busy) return;
    setBusy(true);
    try {
      const startup = await create(idea.trim(), name.trim() || undefined);
      // Only kick off simulation if it hasn't started yet (idempotent guard)
      if (!startup.simulationStarted) {
        await startSimulation(1.0);
      }
      onReady(startup.id);
    } catch {
      setBusy(false);
    }
  };

  const openExisting = async (s: Startup) => {
    setBusy(true);
    try {
      await load(s.id);
      onReady(s.id);
    } catch {
      setBusy(false);
    }
  };

  const working = busy || loading;

  const phaseLabel = (phase: string) => {
    const map: Record<string, string> = {
      IDEA: "Idea", ANALYSIS: "Analysis", DEBATE: "Debate",
      DECISION: "Decision", PLAN: "Plan", COMPLETED: "Completed", EXECUTION: "Execution",
    };
    return map[phase] ?? phase;
  };

  return (
    <div className="min-h-screen flex flex-col items-center justify-center px-6 relative overflow-hidden">
      <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(ellipse_at_top,_rgba(56,189,248,0.12),transparent_55%)]" />
      <div className="relative w-full max-w-2xl">
        <div className="text-center mb-8 animate-fade-in-up">
          <div className="inline-flex items-center gap-2 text-xs font-mono uppercase tracking-widest text-sky-400/80 mb-3">
            <span className="h-1.5 w-1.5 rounded-full bg-sky-400 animate-pulse" />
            Autonomous Virtual Startup
          </div>
          <h1 className="text-4xl sm:text-5xl font-extrabold text-white leading-tight">
            You're the founder.
            <br />
            <span className="bg-gradient-to-r from-sky-400 via-pink-400 to-ceo bg-clip-text text-transparent">
              Your AI team builds the company.
            </span>
          </h1>
          <p className="mt-4 text-slate-400 max-w-xl mx-auto">
            Drop in an idea. Watch your CEO, Development, Marketing, and Finance
            agents analyze it, debate the trade-offs, and converge on a real
            startup blueprint — live, in the office.
          </p>
        </div>

        <form
          onSubmit={submit}
          className="glass rounded-2xl p-5 sm:p-6 shadow-2xl animate-fade-in-up"
        >
          <label className="block text-sm font-medium text-slate-300 mb-2">
            What's your startup idea?
          </label>
          <textarea
            value={idea}
            onChange={(e) => setIdea(e.target.value)}
            placeholder="Describe your idea in a sentence or two…"
            rows={3}
            className="w-full resize-none rounded-xl bg-ink-900 border border-white/10 px-4 py-3 text-slate-100 placeholder:text-slate-600 focus:outline-none focus:ring-2 focus:ring-sky-500/50"
          />

          <div className="mt-3 flex flex-wrap gap-2">
            {EXAMPLES.map((ex) => (
              <button
                key={ex}
                type="button"
                onClick={() => setIdea(ex)}
                className="text-xs text-slate-400 hover:text-white border border-white/10 hover:border-sky-500/50 rounded-full px-3 py-1 transition"
              >
                {ex.length > 42 ? ex.slice(0, 42) + "…" : ex}
              </button>
            ))}
          </div>

          <div className="mt-4 flex flex-col sm:flex-row gap-3">
            <input
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Company name (optional)"
              className="flex-1 rounded-xl bg-ink-900 border border-white/10 px-4 py-3 text-slate-100 placeholder:text-slate-600 focus:outline-none focus:ring-2 focus:ring-sky-500/50"
            />
            <button
              type="submit"
              disabled={working || idea.trim().length < 8}
              className="rounded-xl px-6 py-3 font-semibold bg-gradient-to-r from-sky-500 to-indigo-500 text-white shadow-lg shadow-sky-900/40 disabled:opacity-40 disabled:cursor-not-allowed hover:brightness-110 transition"
            >
              {working ? "Founding…" : "Found the startup →"}
            </button>
          </div>

          {error && (
            <p className="mt-3 text-sm text-rose-400">Couldn't start: {error}</p>
          )}
        </form>

        {/* Reopen by ID */}
        <form
          onSubmit={handleOpenById}
          className="mt-4 glass rounded-2xl p-4 flex items-center gap-3 animate-fade-in-up"
        >
          <div className="flex-1">
            <label className="block text-xs font-mono uppercase tracking-widest text-slate-400 mb-1">
              Reopen startup by ID
            </label>
            <input
              type="number"
              min="1"
              value={lookupId}
              onChange={(e) => setLookupId(e.target.value)}
              placeholder="e.g. 2"
              className="w-full rounded-xl bg-ink-900 border border-white/10 px-3 py-2 text-sm text-slate-100 placeholder:text-slate-600 focus:outline-none focus:ring-2 focus:ring-sky-500/50"
            />
          </div>
          <button
            type="submit"
            disabled={busy || !lookupId.trim()}
            className="self-end rounded-xl px-5 py-2.5 text-xs font-semibold bg-sky-600 hover:bg-sky-500 text-white disabled:opacity-40 transition shadow-md shadow-sky-950/50"
          >
            Reopen ID →
          </button>
        </form>

        {/* Saved Startups */}
        {(loadingSaved || savedStartups.length > 0) && (
          <div className="mt-6 animate-fade-in-up">
            <h2 className="text-xs font-mono uppercase tracking-widest text-slate-500 mb-3">
              {loadingSaved ? "Loading saved startups…" : "Resume a saved startup"}
            </h2>
            {!loadingSaved && (
              <div className="flex flex-col gap-2">
                {savedStartups.map((s) => (
                  <button
                    key={s.id}
                    onClick={() => openExisting(s)}
                    disabled={busy}
                    className="flex items-center justify-between rounded-xl border border-white/10 bg-slate-900/60 hover:border-sky-500/50 hover:bg-slate-800/70 px-4 py-3 text-left transition group disabled:opacity-40"
                  >
                    <div>
                      <p className="text-sm font-semibold text-slate-200 group-hover:text-white">
                        {s.name}
                      </p>
                      <p className="text-xs text-slate-500 mt-0.5 line-clamp-1">
                        {s.originalIdea}
                      </p>
                    </div>
                    <div className="flex flex-col items-end gap-1 ml-4 shrink-0">
                      <span className={`text-xs font-mono px-2 py-0.5 rounded-full border ${
                        s.simulationCompleted
                          ? "text-emerald-400 border-emerald-500/40 bg-emerald-950/40"
                          : s.simulationStarted
                          ? "text-sky-400 border-sky-500/40 bg-sky-950/40"
                          : "text-slate-400 border-slate-600/40 bg-slate-800/40"
                      }`}>
                        {phaseLabel(s.currentPhase)}
                      </span>
                      <span className="text-xs text-slate-600">
                        #{s.id} · {new Date(s.createdAt).toLocaleDateString()}
                      </span>
                    </div>
                  </button>
                ))}
              </div>
            )}
          </div>
        )}

        <p className="text-center text-xs text-slate-600 mt-6">
          Persistent backend · real agent reasoning · browser-refresh safe
        </p>
      </div>
    </div>
  );
}
