import { useState, useMemo, useEffect } from "react";
import { useSim } from "../../store/simulationStore";
import type { AgentType, Decision, ExecutionTraceEntry } from "../../types";

const AGENT_CONFIG: Record<
  AgentType,
  { label: string; name: string; icon: string; color: string; border: string; bg: string }
> = {
  CEO: {
    label: "Chief Executive",
    name: "Alex Vance (CEO)",
    icon: "👑",
    color: "text-amber-400",
    border: "border-amber-500/40",
    bg: "bg-amber-500/10",
  },
  DEVELOPMENT: {
    label: "Engineering",
    name: "Dev Lead & Backend Eng",
    icon: "💻",
    color: "text-sky-400",
    border: "border-sky-500/40",
    bg: "bg-sky-500/10",
  },
  MARKETING: {
    label: "Growth & Marketing",
    name: "Marketing Lead & Growth Lead",
    icon: "📢",
    color: "text-purple-400",
    border: "border-purple-500/40",
    bg: "bg-purple-500/10",
  },
  FINANCE: {
    label: "Finance & Treasury",
    name: "Finance Dir & Controller",
    icon: "💰",
    color: "text-emerald-400",
    border: "border-emerald-500/40",
    bg: "bg-emerald-500/10",
  },
};

type CategoryFilter = "ALL" | "TOOLS" | "DECISIONS" | "MESSAGES" | "TASKS" | "REASONING";

export default function AgentWorkHistoryModal({
  initialAgent = "ALL",
  onClose,
}: {
  initialAgent?: AgentType | "ALL";
  onClose: () => void;
}) {
  const startup = useSim((s) => s.startup);
  const trace = useSim((s) => s.trace);
  const loadTrace = useSim((s) => s.loadTrace);
  const executionState = useSim((s) => s.executionState);
  const messages = useSim((s) => s.messages);
  const debates = useSim((s) => s.debates);
  const agents = useSim((s) => s.agents);

  const [selectedAgent, setSelectedAgent] = useState<AgentType | "ALL">(initialAgent);
  const [selectedCategory, setSelectedCategory] = useState<CategoryFilter>("ALL");
  const [searchQuery, setSearchQuery] = useState("");
  const [selectedEntry, setSelectedEntry] = useState<ExecutionTraceEntry | null>(null);

  // Refresh trace on mount or agent filter change
  useEffect(() => {
    if (selectedAgent === "ALL") {
      void loadTrace();
    } else {
      void loadTrace(selectedAgent);
    }
  }, [selectedAgent, loadTrace]);

  // Keyboard shortcut to close on ESC
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [onClose]);

  // Execution tasks for selected agent
  const agentExecutionTasks = useMemo(() => {
    const all = executionState?.tasks || [];
    if (selectedAgent === "ALL") return all;
    return all.filter((t) => t.department === selectedAgent);
  }, [executionState, selectedAgent]);

  // Messages involving selected agent
  const agentMessages = useMemo(() => {
    if (selectedAgent === "ALL") return messages;
    return messages.filter(
      (m) => m.agentType === selectedAgent || m.targetAgent === selectedAgent
    );
  }, [messages, selectedAgent]);

  // Decisions involving selected agent
  const agentDecisions = useMemo(() => {
    const boardroomDecisions = debates
      .map((d) => d.decision)
      .filter((d): d is Decision => d !== null);
    if (selectedAgent === "ALL") return boardroomDecisions;
    return boardroomDecisions.filter(
      (d) => d.decidedBy === selectedAgent || d.affectedDepartments?.includes(selectedAgent)
    );
  }, [debates, selectedAgent]);

  // Filtered trace entries
  const filteredTrace = useMemo(() => {
    return trace.filter((entry) => {
      // Agent filter (if not already filtered by backend)
      if (selectedAgent !== "ALL") {
        const matches =
          (entry.agent && entry.agent.toUpperCase() === selectedAgent) ||
          (entry.sender && entry.sender.toUpperCase() === selectedAgent) ||
          (entry.recipient && entry.recipient.toUpperCase() === selectedAgent);
        if (!matches) return false;
      }

      // Category filter
      if (selectedCategory === "TOOLS") {
        if (entry.type !== "TOOL_CALL" && entry.type !== "TOOL_RESULT") return false;
      } else if (selectedCategory === "DECISIONS") {
        if (entry.type !== "ORCHESTRATION_DECISION" && !entry.decision) return false;
      } else if (selectedCategory === "MESSAGES") {
        if (entry.type !== "AGENT_MESSAGE" && entry.type !== "HANDOFF") return false;
      } else if (selectedCategory === "TASKS") {
        if (entry.type !== "TASK_STATE_CHANGE") return false;
      } else if (selectedCategory === "REASONING") {
        if (
          entry.type !== "LLM_REASONING" &&
          entry.type !== "AGENT_ACTION" &&
          entry.type !== "REPLAN" &&
          entry.type !== "RECOVERY"
        )
          return false;
      }

      // Search query
      if (searchQuery.trim()) {
        const q = searchQuery.toLowerCase();
        const textToSearch = [
          entry.action,
          entry.reason,
          entry.decision,
          entry.toolName,
          entry.toolArguments,
          entry.toolResult,
          entry.outcome,
          entry.payload,
        ]
          .filter(Boolean)
          .join(" ")
          .toLowerCase();
        if (!textToSearch.includes(q)) return false;
      }

      return true;
    });
  }, [trace, selectedAgent, selectedCategory, searchQuery]);

  // Current active agent info
  const currentAgentInfo = useMemo(() => {
    if (selectedAgent === "ALL") return null;
    return agents.find((a) => a.type === selectedAgent);
  }, [agents, selectedAgent]);

  // Metrics summary
  const summaryCounts = useMemo(() => {
    const toolCalls = trace.filter(
      (t) =>
        (selectedAgent === "ALL" || t.agent === selectedAgent) &&
        (t.type === "TOOL_CALL" || t.type === "TOOL_RESULT")
    ).length;
    const completedTasks = agentExecutionTasks.filter((t) => t.status === "COMPLETED").length;
    const totalExecutionTasks = agentExecutionTasks.length;
    const decisionsCount = agentDecisions.length;
    const messagesCount = agentMessages.length;

    return { toolCalls, completedTasks, totalExecutionTasks, decisionsCount, messagesCount };
  }, [trace, selectedAgent, agentExecutionTasks, agentDecisions, agentMessages]);

  const formatTimestamp = (iso: string) => {
    try {
      const d = new Date(iso);
      return d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", second: "2-digit" });
    } catch {
      return iso;
    }
  };

  const getTypeBadge = (type: string) => {
    switch (type) {
      case "TOOL_CALL":
      case "TOOL_RESULT":
        return <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-amber-500/20 text-amber-300 border border-amber-500/30">🛠️ Tool</span>;
      case "LLM_REASONING":
        return <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-sky-500/20 text-sky-300 border border-sky-500/30">🧠 Reasoning</span>;
      case "ORCHESTRATION_DECISION":
        return <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-indigo-500/20 text-indigo-300 border border-indigo-500/30">💡 Decision</span>;
      case "TASK_STATE_CHANGE":
        return <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-emerald-500/20 text-emerald-300 border border-emerald-500/30">📋 Task</span>;
      case "HANDOFF":
      case "AGENT_MESSAGE":
        return <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-purple-500/20 text-purple-300 border border-purple-500/30">💬 Message</span>;
      case "REPLAN":
      case "RECOVERY":
        return <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-rose-500/20 text-rose-300 border border-rose-500/30">⚠️ Recovery</span>;
      default:
        return <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-slate-800 text-slate-300 border border-slate-700">{type}</span>;
    }
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      data-testid="agent-work-history-modal"
      className="fixed inset-0 z-50 flex items-center justify-center p-3 md:p-6 bg-black/80 backdrop-blur-md animate-fade-in"
    >
      <div className="bg-slate-950 border border-slate-800 rounded-2xl w-full max-w-6xl h-[92vh] flex flex-col shadow-2xl overflow-hidden">
        {/* Modal Header */}
        <div className="px-6 py-4 border-b border-slate-800/80 bg-slate-900/60 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="h-10 w-10 rounded-xl bg-gradient-to-br from-indigo-500 to-purple-600 flex items-center justify-center text-xl shadow-lg shadow-indigo-500/20">
              📜
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h2 className="text-lg font-bold text-white tracking-wide">
                  Agent Work History & Audit Trace
                </h2>
                <span className="px-2 py-0.5 rounded-full text-[11px] font-bold bg-indigo-500/20 text-indigo-300 border border-indigo-500/30">
                  Startup #{startup?.id}: {startup?.name}
                </span>
              </div>
              <p className="text-xs text-slate-400">
                Verified chronological record of agent reasoning, messages, tool invocations, decisions, and task mutations.
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            data-testid="close-history-modal"
            className="h-8 w-8 rounded-lg bg-slate-800/80 hover:bg-slate-700 text-slate-400 hover:text-white flex items-center justify-center transition"
            title="Close (ESC)"
          >
            ✕
          </button>
        </div>

        {/* Agent Selector Bar */}
        <div className="px-6 py-3 border-b border-slate-800/60 bg-slate-900/40 flex items-center gap-2 overflow-x-auto scroll-slim">
          <button
            onClick={() => setSelectedAgent("ALL")}
            data-testid="tab-agent-all"
            className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition shrink-0 flex items-center gap-1.5 ${
              selectedAgent === "ALL"
                ? "bg-indigo-600 text-white shadow-md shadow-indigo-600/30"
                : "bg-slate-800/60 text-slate-400 hover:text-slate-200 hover:bg-slate-800"
            }`}
          >
            🌐 All Agents Overview
          </button>

          {(Object.keys(AGENT_CONFIG) as AgentType[]).map((agentKey) => {
            const cfg = AGENT_CONFIG[agentKey];
            const isSelected = selectedAgent === agentKey;
            return (
              <button
                key={agentKey}
                onClick={() => setSelectedAgent(agentKey)}
                data-testid={`tab-agent-${agentKey.toLowerCase()}`}
                className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition shrink-0 flex items-center gap-1.5 border ${
                  isSelected
                    ? `${cfg.bg} ${cfg.color} ${cfg.border} shadow-md`
                    : "border-slate-800 bg-slate-900/50 text-slate-400 hover:text-slate-200 hover:bg-slate-800"
                }`}
              >
                <span>{cfg.icon}</span>
                <span>{cfg.label}</span>
                <span className="text-[10px] opacity-70">({agentKey})</span>
              </button>
            );
          })}
        </div>

        {/* Agent Status & Metrics Strip */}
        <div className="px-6 py-3 bg-slate-900/20 border-b border-slate-800/60 flex flex-wrap items-center justify-between gap-4 text-xs">
          {selectedAgent === "ALL" ? (
            <div className="text-slate-300 flex items-center gap-2">
              <span className="font-semibold text-white">Full Cross-Department Trajectory:</span>
              <span>All 4 agents collaborating under Startup #{startup?.id}</span>
            </div>
          ) : (
            <div className="flex items-center gap-3">
              <span className="text-base">{AGENT_CONFIG[selectedAgent].icon}</span>
              <div>
                <span className="font-bold text-white mr-2">
                  {currentAgentInfo?.name || AGENT_CONFIG[selectedAgent].name}
                </span>
                <span className="text-slate-400">{currentAgentInfo?.mandate}</span>
              </div>
              <span
                className={`px-2 py-0.5 rounded text-[10px] font-bold uppercase ${
                  currentAgentInfo?.state === "WORKING"
                    ? "bg-emerald-500/20 text-emerald-300 border border-emerald-500/30"
                    : currentAgentInfo?.state === "BLOCKED"
                    ? "bg-rose-500/20 text-rose-300 border border-rose-500/30"
                    : "bg-slate-800 text-slate-400 border border-slate-700"
                }`}
              >
                {currentAgentInfo?.state || "IDLE"}
              </span>
            </div>
          )}

          {/* Metric Badges */}
          <div className="flex items-center gap-3 text-[11px] text-slate-300">
            <span className="bg-slate-900 px-2.5 py-1 rounded-md border border-slate-800">
              📋 Tasks:{" "}
              <b className="text-emerald-400">{summaryCounts.completedTasks}</b>/
              {summaryCounts.totalExecutionTasks} done
            </span>
            <span className="bg-slate-900 px-2.5 py-1 rounded-md border border-slate-800">
              🛠️ Tools: <b className="text-amber-400">{summaryCounts.toolCalls}</b>
            </span>
            <span className="bg-slate-900 px-2.5 py-1 rounded-md border border-slate-800">
              💡 Decisions: <b className="text-indigo-400">{summaryCounts.decisionsCount}</b>
            </span>
            <span className="bg-slate-900 px-2.5 py-1 rounded-md border border-slate-800">
              💬 Messages: <b className="text-purple-400">{summaryCounts.messagesCount}</b>
            </span>
          </div>
        </div>

        {/* Provenance Disclosure Banner (Clearly distinguishes recorded facts from untracked data) */}
        <div className="px-6 py-2 bg-slate-900/40 border-b border-slate-800/40 flex items-center justify-between text-[11px] text-slate-400">
          <div className="flex items-center gap-2">
            <span className="text-emerald-400 font-bold">✓ RECORDED FACTS:</span>
            <span>Authoritative database records (IDs, timestamps, prompts, verified tool results, task statuses).</span>
          </div>
          <div className="flex items-center gap-2 text-slate-500">
            <span className="text-slate-400 font-bold">✕ NOT TRACKED:</span>
            <span>Git code diffs, real banking transactions, and physical employee hours.</span>
          </div>
        </div>

        {/* Filters and Search Bar */}
        <div className="px-6 py-3 bg-slate-950 flex flex-wrap items-center justify-between gap-3 border-b border-slate-800/40">
          <div className="flex items-center gap-1.5 overflow-x-auto scroll-slim">
            {(["ALL", "TOOLS", "DECISIONS", "MESSAGES", "TASKS", "REASONING"] as CategoryFilter[]).map(
              (cat) => (
                <button
                  key={cat}
                  onClick={() => setSelectedCategory(cat)}
                  data-testid={`filter-cat-${cat.toLowerCase()}`}
                  className={`px-2.5 py-1 rounded-md text-[11px] font-medium transition ${
                    selectedCategory === cat
                      ? "bg-slate-800 text-white border border-slate-700"
                      : "text-slate-400 hover:text-slate-200 hover:bg-slate-900"
                  }`}
                >
                  {cat === "ALL"
                    ? "All Activity"
                    : cat === "TOOLS"
                    ? "🛠️ Tools"
                    : cat === "DECISIONS"
                    ? "💡 Decisions"
                    : cat === "MESSAGES"
                    ? "💬 Messages"
                    : cat === "TASKS"
                    ? "📋 Tasks"
                    : "🧠 Reasoning"}
                </button>
              )
            )}
          </div>

          <div className="relative">
            <input
              type="text"
              placeholder="Search recorded actions, tools, reasons…"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="px-3 py-1 text-xs rounded-lg bg-slate-900 border border-slate-800 text-slate-200 placeholder-slate-500 focus:outline-none focus:border-indigo-500 w-64"
            />
            {searchQuery && (
              <button
                onClick={() => setSearchQuery("")}
                className="absolute right-2 top-1 text-xs text-slate-500 hover:text-slate-300"
              >
                ✕
              </button>
            )}
          </div>
        </div>

        {/* Main Content Area: Left Timeline + Right Inspector */}
        <div className="flex-1 min-h-0 flex overflow-hidden">
          {/* Timeline / Activity List */}
          <div className="flex-1 overflow-y-auto scroll-slim p-4 space-y-2.5 border-r border-slate-800/60">
            {filteredTrace.length === 0 ? (
              <div className="h-64 flex flex-col items-center justify-center text-slate-500 text-center p-6">
                <span className="text-3xl mb-2">📭</span>
                <p className="text-sm font-medium text-slate-400">
                  No recorded activities found matching the criteria.
                </p>
                <p className="text-xs text-slate-600 mt-1 max-w-sm">
                  {selectedAgent !== "ALL"
                    ? `No work records logged for ${selectedAgent} yet under this filter.`
                    : "Simulate or resume the startup to observe live agent work history."}
                </p>
              </div>
            ) : (
              filteredTrace.map((entry) => {
                const isSelected = selectedEntry?.id === entry.id;
                return (
                  <div
                    key={entry.id}
                    data-testid={`trace-entry-${entry.id}`}
                    onClick={() => setSelectedEntry(entry)}
                    className={`p-3 rounded-xl border transition cursor-pointer ${
                      isSelected
                        ? "bg-indigo-950/30 border-indigo-500/60 shadow-lg shadow-indigo-950/40"
                        : "bg-slate-900/40 hover:bg-slate-900/80 border-slate-800/70"
                    }`}
                  >
                    <div className="flex items-center justify-between gap-2 mb-1.5">
                      <div className="flex items-center gap-2">
                        <span className="text-[10px] font-mono text-slate-500 font-bold">
                          #{entry.sequenceNumber}
                        </span>
                        {getTypeBadge(entry.type)}
                        {entry.agent && (
                          <span
                            className={`text-[10px] font-bold px-1.5 py-0.5 rounded ${
                              AGENT_CONFIG[entry.agent as AgentType]?.color || "text-slate-400"
                            } bg-black/40`}
                          >
                            {entry.agent}
                          </span>
                        )}
                        {entry.sender && entry.recipient && (
                          <span className="text-[10px] text-purple-400 font-mono">
                            {entry.sender} ➔ {entry.recipient}
                          </span>
                        )}
                      </div>
                      <span className="text-[10px] text-slate-500 font-mono">
                        {formatTimestamp(entry.timestamp)}
                      </span>
                    </div>

                    <div className="text-xs font-semibold text-slate-200 leading-snug">
                      {entry.action || entry.reason || "Agent Activity"}
                    </div>

                    {/* Brief context preview */}
                    {entry.decision && (
                      <p className="text-[11px] text-indigo-300 mt-1 line-clamp-2 bg-indigo-950/20 p-1.5 rounded border border-indigo-900/30">
                        <b>Decision:</b> {entry.decision}
                      </p>
                    )}

                    {entry.toolName && (
                      <div className="text-[11px] text-amber-300 mt-1 flex items-center gap-1.5">
                        <span className="font-mono text-amber-400 font-bold">
                          🛠️ {entry.toolName}
                        </span>
                        {entry.outcome && (
                          <span className="text-[10px] px-1.5 rounded bg-amber-500/20 border border-amber-500/30">
                            {entry.outcome}
                          </span>
                        )}
                      </div>
                    )}

                    {entry.reason && entry.action !== entry.reason && (
                      <p className="text-[11px] text-slate-400 mt-1 line-clamp-1 italic">
                        {entry.reason}
                      </p>
                    )}
                  </div>
                );
              })
            )}
          </div>

          {/* Right Inspector Panel */}
          <div className="w-96 shrink-0 flex flex-col bg-slate-950 p-5 overflow-y-auto scroll-slim">
            {selectedEntry ? (
              <div className="space-y-4">
                <div className="flex items-center justify-between pb-3 border-b border-slate-800">
                  <h3 className="text-xs font-bold uppercase tracking-wider text-indigo-400">
                    Record Details Inspector
                  </h3>
                  <span className="text-xs font-mono text-slate-500">
                    Sequence #{selectedEntry.sequenceNumber}
                  </span>
                </div>

                <div>
                  <label className="text-[10px] font-bold uppercase text-slate-500 block mb-1">
                    Activity Type
                  </label>
                  <div className="flex items-center gap-2">
                    {getTypeBadge(selectedEntry.type)}
                    <span className="text-xs font-mono text-slate-300">{selectedEntry.type}</span>
                  </div>
                </div>

                <div>
                  <label className="text-[10px] font-bold uppercase text-slate-500 block mb-1">
                    Timestamp & DB Provenance
                  </label>
                  <p className="text-xs font-mono text-slate-300">
                    {new Date(selectedEntry.timestamp).toLocaleString()}
                  </p>
                  <p className="text-[10px] font-mono text-slate-500 mt-0.5">
                    ID: {selectedEntry.id} • Trace ID: {selectedEntry.traceId} • Startup ID:{" "}
                    {selectedEntry.startupId}
                  </p>
                </div>

                {selectedEntry.agent && (
                  <div>
                    <label className="text-[10px] font-bold uppercase text-slate-500 block mb-1">
                      Responsible Agent
                    </label>
                    <span
                      className={`text-xs font-bold px-2 py-0.5 rounded ${
                        AGENT_CONFIG[selectedEntry.agent as AgentType]?.bg || "bg-slate-800"
                      } ${
                        AGENT_CONFIG[selectedEntry.agent as AgentType]?.color || "text-slate-200"
                      } border border-white/10`}
                    >
                      {selectedEntry.agent}
                    </span>
                  </div>
                )}

                {selectedEntry.action && (
                  <div>
                    <label className="text-[10px] font-bold uppercase text-slate-500 block mb-1">
                      Action Context
                    </label>
                    <p className="text-xs font-medium text-white">{selectedEntry.action}</p>
                  </div>
                )}

                {selectedEntry.reason && (
                  <div>
                    <label className="text-[10px] font-bold uppercase text-slate-500 block mb-1">
                      Reasoning / Rationale
                    </label>
                    <p className="text-xs text-slate-300 bg-slate-900/80 p-2.5 rounded-lg border border-slate-800 leading-relaxed whitespace-pre-wrap">
                      {selectedEntry.reason}
                    </p>
                  </div>
                )}

                {selectedEntry.decision && (
                  <div>
                    <label className="text-[10px] font-bold uppercase text-slate-500 block mb-1">
                      Synthesized Decision
                    </label>
                    <p className="text-xs text-indigo-300 bg-indigo-950/30 p-2.5 rounded-lg border border-indigo-900/40 leading-relaxed whitespace-pre-wrap">
                      {selectedEntry.decision}
                    </p>
                  </div>
                )}

                {selectedEntry.toolName && (
                  <div className="space-y-2">
                    <label className="text-[10px] font-bold uppercase text-amber-500 block">
                      Tool Execution Details
                    </label>
                    <div className="bg-amber-950/20 p-2.5 rounded-lg border border-amber-900/40 text-xs">
                      <div className="font-mono text-amber-300 font-bold mb-1">
                        Tool: {selectedEntry.toolName}
                      </div>
                      {selectedEntry.toolArguments && (
                        <div className="mt-1.5">
                          <span className="text-[10px] text-slate-400 block mb-0.5">Input Arguments:</span>
                          <pre className="font-mono text-[10px] bg-black/40 p-2 rounded text-slate-300 overflow-x-auto">
                            {selectedEntry.toolArguments}
                          </pre>
                        </div>
                      )}
                      {selectedEntry.toolResult && (
                        <div className="mt-1.5">
                          <span className="text-[10px] text-slate-400 block mb-0.5">Tool Output:</span>
                          <pre className="font-mono text-[10px] bg-black/40 p-2 rounded text-emerald-300 overflow-x-auto">
                            {selectedEntry.toolResult}
                          </pre>
                        </div>
                      )}
                      {selectedEntry.outcome && (
                        <div className="mt-1 text-[10px] text-slate-400">
                          Outcome Status: <b className="text-white">{selectedEntry.outcome}</b>
                        </div>
                      )}
                    </div>
                  </div>
                )}

                {selectedEntry.payload && (
                  <div>
                    <label className="text-[10px] font-bold uppercase text-slate-500 block mb-1">
                      Raw Verified Payload
                    </label>
                    <pre className="font-mono text-[10px] bg-slate-900/90 p-2.5 rounded-lg text-slate-400 border border-slate-800 overflow-x-auto max-h-48 scroll-slim">
                      {selectedEntry.payload}
                    </pre>
                  </div>
                )}
              </div>
            ) : (
              <div className="h-full flex flex-col items-center justify-center text-slate-500 text-center p-4">
                <span className="text-2xl mb-1">🔍</span>
                <p className="text-xs font-medium text-slate-400">
                  Select an activity on the left to inspect detailed parameters, arguments, decisions, and tool outputs.
                </p>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
