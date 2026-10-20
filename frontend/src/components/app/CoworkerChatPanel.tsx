import { useState, useEffect, useRef } from "react";
import { api } from "../../services/api";
import type { CoworkerChatMessage } from "../../types";

interface CoworkerChatPanelProps {
  startupId: number;
  isAppGenerated: boolean;
  onAppUpdated?: () => void;
  onOpenPreview?: () => void;
  onSelectFile?: (path: string) => void;
}

export default function CoworkerChatPanel({
  startupId,
  isAppGenerated,
  onAppUpdated,
  onOpenPreview,
  onSelectFile,
}: CoworkerChatPanelProps) {
  const [messages, setMessages] = useState<CoworkerChatMessage[]>([]);
  const [inputMessage, setInputMessage] = useState("");
  const [selectedAgent, setSelectedAgent] = useState<string>("Alex Rivera (Lead Engineer)");
  const [loading, setLoading] = useState(false);
  const [actionInProgress, setActionInProgress] = useState<number | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  // Quick suggestions
  const suggestions = [
    "✨ Add an AI Product Scanner section with real-time barcode/spec inspection",
    "🎨 Modernize the visual design with dark cyberpunk theme and smooth glow accents",
    "📊 Add a real-time analytics metrics dashboard with conversion charts",
    "💬 Add a customer contact and feedback form with instant validation",
  ];

  const agentOptions = [
    { name: "Alex Rivera", role: "Lead Engineer 💻", id: "Alex Rivera (Lead Engineer)" },
    { name: "Sarah Chen", role: "UI/UX Designer 🎨", id: "Sarah Chen (UI/UX Designer)" },
    { name: "Marcus Vance", role: "Chief Executive 👔", id: "Marcus Vance (CEO)" },
    { name: "Maya Lin", role: "Growth & Product 📈", id: "Maya Lin (Product & Growth)" },
  ];

  const fetchChatHistory = async () => {
    try {
      const data = await api.getCoworkerChat(startupId);
      setMessages(data);
    } catch (err) {
      console.error("Failed to load coworker chat history:", err);
    }
  };

  useEffect(() => {
    void fetchChatHistory();
    const interval = setInterval(fetchChatHistory, 5000);
    return () => clearInterval(interval);
  }, [startupId]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, loading]);

  const handleSendMessage = async (textToSend?: string) => {
    const text = (textToSend || inputMessage).trim();
    if (!text || loading) return;

    setInputMessage("");
    setLoading(true);
    setErrorMsg(null);

    // Optimistic user message
    const tempUserMsg: CoworkerChatMessage = {
      id: Date.now(),
      startupId,
      role: "USER",
      agentType: null,
      agentName: null,
      content: text,
      changeRequestId: null,
      createdAt: new Date().toISOString(),
    };
    setMessages((prev) => [...prev, tempUserMsg]);

    try {
      const response = await api.sendCoworkerChatMessage(startupId, text, selectedAgent);
      setMessages((prev) => [...prev.filter((m) => m.id !== tempUserMsg.id), response]);
      await fetchChatHistory();
    } catch (err) {
      setErrorMsg((err as Error).message || "Failed to communicate with coworker agent.");
    } finally {
      setLoading(false);
    }
  };

  const handleApprove = async (requestId: number) => {
    setActionInProgress(requestId);
    setErrorMsg(null);
    try {
      const updatedReq = await api.approveAppChangeRequest(startupId, requestId);
      setMessages((prev) =>
        prev.map((msg) =>
          msg.changeRequest?.id === requestId
            ? { ...msg, changeRequest: updatedReq }
            : msg
        )
      );
      if (onAppUpdated) onAppUpdated();
      await fetchChatHistory();
    } catch (err) {
      setErrorMsg((err as Error).message || "Failed to approve and apply code changes.");
    } finally {
      setActionInProgress(null);
    }
  };

  const handleReject = async (requestId: number) => {
    setActionInProgress(requestId);
    setErrorMsg(null);
    try {
      const updatedReq = await api.rejectAppChangeRequest(startupId, requestId);
      setMessages((prev) =>
        prev.map((msg) =>
          msg.changeRequest?.id === requestId
            ? { ...msg, changeRequest: updatedReq }
            : msg
        )
      );
      await fetchChatHistory();
    } catch (err) {
      setErrorMsg((err as Error).message || "Failed to reject proposed change.");
    } finally {
      setActionInProgress(null);
    }
  };

  const getStatusBadge = (status: string) => {
    switch (status) {
      case "PROPOSED":
        return (
          <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-amber-500/20 text-amber-300 border border-amber-500/40 animate-pulse">
            Awaiting User Approval
          </span>
        );
      case "APPROVED":
      case "APPLYING":
        return (
          <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-sky-500/20 text-sky-300 border border-sky-500/40 animate-pulse">
            🔨 Building & Verifying…
          </span>
        );
      case "SUCCEEDED":
        return (
          <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-emerald-500/20 text-emerald-300 border border-emerald-500/40">
            ✓ Changes Built & Deployed
          </span>
        );
      case "FAILED":
        return (
          <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-rose-500/20 text-rose-300 border border-rose-500/40">
            Build Failed (Rolled Back)
          </span>
        );
      case "REJECTED":
        return (
          <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-slate-700 text-slate-300 border border-slate-600">
            Plan Rejected
          </span>
        );
      default:
        return null;
    }
  };

  return (
    <div className="flex-1 flex flex-col min-h-0 bg-slate-950 text-slate-200">
      {/* Sub-header with security guarantees and agent picker */}
      <div className="px-4 py-2.5 bg-slate-900/60 border-b border-slate-800/80 flex flex-wrap items-center justify-between gap-3 text-xs">
        <div className="flex items-center gap-2">
          <span className="text-sm">🤖</span>
          <span className="font-semibold text-slate-300">Target Agent:</span>
          <select
            value={selectedAgent}
            onChange={(e) => setSelectedAgent(e.target.value)}
            className="bg-slate-800 text-slate-200 text-xs rounded-lg px-2.5 py-1 border border-slate-700 focus:outline-none focus:border-indigo-500"
          >
            {agentOptions.map((opt) => (
              <option key={opt.id} value={opt.id}>
                {opt.name} ({opt.role})
              </option>
            ))}
          </select>
        </div>

        <div className="flex items-center gap-2 text-[11px] text-slate-400">
          <span className="px-2 py-0.5 rounded bg-emerald-500/10 text-emerald-400 border border-emerald-500/30 font-medium">
            🔒 Approval Required
          </span>
          <span className="hidden sm:inline text-slate-500">|</span>
          <span className="hidden sm:inline text-slate-400 font-mono">
            Isolated in ./workspaces/startup-{startupId}/
          </span>
        </div>
      </div>

      {/* Error alert if any */}
      {errorMsg && (
        <div className="px-4 py-2 bg-rose-500/15 border-b border-rose-500/40 text-rose-300 text-xs flex items-center justify-between">
          <span>⚠️ {errorMsg}</span>
          <button onClick={() => setErrorMsg(null)} className="text-rose-400 hover:text-white font-bold ml-2">
            ✕
          </button>
        </div>
      )}

      {/* Chat Messages Log */}
      <div className="flex-1 overflow-y-auto scroll-slim p-4 space-y-4">
        {messages.length === 0 && (
          <div className="h-full flex flex-col items-center justify-center text-center p-6 space-y-4">
            <div className="h-16 w-16 rounded-2xl bg-indigo-500/10 border border-indigo-500/20 flex items-center justify-center text-3xl shadow-inner">
              💬
            </div>
            <div className="max-w-md space-y-1">
              <h3 className="text-base font-bold text-white tracking-wide">
                Collaborate with Your Virtual Startup Coworkers
              </h3>
              <p className="text-xs text-slate-400 leading-relaxed">
                Instruct your Lead Engineer, UI/UX Designer, or CEO in natural language.
                The agent will inspect the actual generated code in your isolated workspace, propose a verified change plan, and await your explicit approval before modifying files.
              </p>
            </div>

            {/* Quick Suggestion Pills */}
            <div className="w-full max-w-xl space-y-2 pt-2">
              <div className="text-[11px] font-semibold uppercase tracking-wider text-slate-500">
                Suggested Prompts:
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 text-left">
                {suggestions.map((sug, idx) => (
                  <button
                    key={idx}
                    onClick={() => handleSendMessage(sug)}
                    className="p-2.5 rounded-xl bg-slate-900 hover:bg-slate-800 border border-slate-800 hover:border-indigo-500/40 text-slate-300 hover:text-white text-xs transition text-left group flex items-start gap-2 shadow-sm"
                  >
                    <span className="text-indigo-400 group-hover:scale-110 transition-transform">💡</span>
                    <span className="line-clamp-2">{sug}</span>
                  </button>
                ))}
              </div>
            </div>
          </div>
        )}

        {messages.map((msg) => {
          const isUser = msg.role === "USER";
          const req = msg.changeRequest;

          return (
            <div
              key={msg.id}
              className={`flex flex-col ${isUser ? "items-end" : "items-start"} max-w-full`}
            >
              <div className="flex items-center gap-2 mb-1 px-1">
                {!isUser && (
                  <span className="text-xs font-bold text-indigo-400 flex items-center gap-1">
                    <span>{msg.agentName || "AI Coworker"}</span>
                    <span className="text-[10px] font-normal text-slate-400">
                      ({msg.agentType || "ENGINEER"})
                    </span>
                  </span>
                )}
                {isUser && <span className="text-xs font-semibold text-slate-300">You (Founder)</span>}
                <span className="text-[10px] text-slate-500">
                  {new Date(msg.createdAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
                </span>
              </div>

              {/* Message Bubble */}
              <div
                className={`rounded-2xl p-4 text-xs max-w-2xl shadow-lg leading-relaxed ${
                  isUser
                    ? "bg-indigo-600 text-white rounded-tr-sm"
                    : "bg-slate-900 border border-slate-800/90 text-slate-200 rounded-tl-sm space-y-3"
                }`}
              >
                <div className="whitespace-pre-wrap">{msg.content}</div>

                {/* Change Request Card if attached */}
                {req && (
                  <div className="mt-3 p-3.5 bg-slate-950/80 rounded-xl border border-slate-800 space-y-3">
                    <div className="flex items-center justify-between gap-2 border-b border-slate-800/60 pb-2">
                      <div className="flex items-center gap-2">
                        <span className="font-bold text-slate-200 text-xs">
                          Change Plan #{req.id}
                        </span>
                        {getStatusBadge(req.status)}
                      </div>
                      <span className="text-[10px] text-slate-400 font-mono">
                        by {req.agentName}
                      </span>
                    </div>

                    {req.planSummary && (
                      <p className="text-[11px] text-slate-300 leading-relaxed italic">
                        &quot;{req.planSummary}&quot;
                      </p>
                    )}

                    {/* Proposed files */}
                    {req.proposedChanges && req.proposedChanges.length > 0 && (
                      <div className="space-y-1.5">
                        <div className="text-[10px] font-bold uppercase tracking-wider text-slate-400">
                          Proposed File Modifications ({req.proposedChanges.length}):
                        </div>
                        <div className="space-y-1">
                          {req.proposedChanges.map((file, idx) => (
                            <div
                              key={idx}
                              onClick={() => onSelectFile && onSelectFile(file.relativePath)}
                              className="px-2.5 py-1.5 rounded-lg bg-slate-900 border border-slate-800 flex items-center justify-between gap-2 font-mono text-[11px] hover:border-slate-700 cursor-pointer"
                              title="Click to view file in editor"
                            >
                              <div className="flex items-center gap-1.5 truncate">
                                <span className={`px-1.5 py-0.2 rounded text-[9px] font-bold uppercase ${
                                  file.action === "MODIFY"
                                    ? "bg-amber-500/20 text-amber-300"
                                    : file.action === "CREATE"
                                    ? "bg-emerald-500/20 text-emerald-300"
                                    : "bg-rose-500/20 text-rose-300"
                                }`}>
                                  {file.action}
                                </span>
                                <span className="text-slate-200 truncate">{file.relativePath}</span>
                              </div>
                              <span className="text-[10px] text-slate-400 font-sans truncate max-w-[200px]">
                                {file.description}
                              </span>
                            </div>
                          ))}
                        </div>
                      </div>
                    )}

                    {/* Build Logs snippet if available */}
                    {req.buildOutput && (
                      <div className="space-y-1">
                        <div className="text-[10px] font-bold uppercase tracking-wider text-slate-400">
                          Build Log:
                        </div>
                        <pre className="p-2.5 bg-black/90 rounded-lg text-[10px] font-mono text-slate-300 overflow-x-auto max-h-32 scroll-slim leading-tight whitespace-pre-wrap">
                          {req.buildOutput}
                        </pre>
                      </div>
                    )}

                    {/* Action buttons if PROPOSED */}
                    {req.status === "PROPOSED" && (
                      <div className="pt-2 border-t border-slate-800/80 flex items-center justify-between gap-2">
                        <div className="text-[10px] text-amber-400/90 flex items-center gap-1">
                          <span>⚠️</span>
                          <span>Review changes before applying to production</span>
                        </div>
                        <div className="flex items-center gap-2">
                          <button
                            onClick={() => handleReject(req.id)}
                            disabled={actionInProgress === req.id}
                            className="px-3 py-1.5 rounded-lg text-xs font-semibold bg-slate-800 hover:bg-slate-700 text-slate-300 border border-slate-700 transition disabled:opacity-50"
                          >
                            Reject
                          </button>
                          <button
                            onClick={() => handleApprove(req.id)}
                            disabled={actionInProgress === req.id}
                            className="px-3.5 py-1.5 rounded-lg text-xs font-bold bg-emerald-600 hover:bg-emerald-500 text-white shadow-md shadow-emerald-600/30 transition flex items-center gap-1.5 disabled:opacity-50"
                          >
                            <span>{actionInProgress === req.id ? "🔨 Applying & Building…" : "✓ Approve & Apply Changes"}</span>
                          </button>
                        </div>
                      </div>
                    )}

                    {/* Success state - Live Preview CTA */}
                    {req.status === "SUCCEEDED" && (
                      <div className="pt-2 border-t border-slate-800/80 flex items-center justify-between">
                        <span className="text-[11px] text-emerald-400 font-semibold flex items-center gap-1">
                          <span>✓</span>
                          <span>Build passed. Production bundle updated.</span>
                        </span>
                        {onOpenPreview && (
                          <button
                            onClick={onOpenPreview}
                            className="px-3 py-1.5 rounded-lg text-xs font-bold bg-indigo-600 hover:bg-indigo-500 text-white shadow-md shadow-indigo-600/30 transition flex items-center gap-1"
                          >
                            <span>Open Live Preview</span>
                            <span>🌐</span>
                          </button>
                        )}
                      </div>
                    )}

                    {/* Failed state - rollback confirmation */}
                    {req.status === "FAILED" && (
                      <div className="pt-2 border-t border-slate-800/80 text-[11px] text-rose-300 flex items-center gap-1">
                        <span>🛡️</span>
                        <span>Safety Rollback Applied: Previous working app workspace was automatically restored.</span>
                      </div>
                    )}
                  </div>
                )}
              </div>
            </div>
          );
        })}

        {/* Loading indicator when agent is planning */}
        {loading && (
          <div className="flex flex-col items-start space-y-1">
            <span className="text-xs font-bold text-indigo-400">
              {selectedAgent} is thinking…
            </span>
            <div className="px-4 py-3 bg-slate-900 border border-slate-800 rounded-2xl rounded-tl-sm flex items-center gap-2 text-xs text-slate-300">
              <span className="h-2 w-2 rounded-full bg-indigo-400 animate-ping" />
              <span>Inspecting generated workspace and drafting code changes…</span>
            </div>
          </div>
        )}

        <div ref={messagesEndRef} />
      </div>

      {/* Chat Input Bar */}
      <div className="p-3 border-t border-slate-800/80 bg-slate-900/60">
        <form
          onSubmit={(e) => {
            e.preventDefault();
            void handleSendMessage();
          }}
          className="flex items-center gap-2"
        >
          <input
            type="text"
            value={inputMessage}
            onChange={(e) => setInputMessage(e.target.value)}
            placeholder={
              isAppGenerated
                ? `Ask ${selectedAgent.split(" ")[0]} to change design, add pages, or fix features…`
                : "Generate your app first before requesting code modifications…"
            }
            disabled={loading || !isAppGenerated}
            className="flex-1 bg-slate-950 border border-slate-800 rounded-xl px-4 py-2.5 text-xs text-white placeholder-slate-500 focus:outline-none focus:border-indigo-500 transition disabled:opacity-50"
          />
          <button
            type="submit"
            disabled={!inputMessage.trim() || loading || !isAppGenerated}
            className="px-4 py-2.5 rounded-xl text-xs font-bold bg-indigo-600 hover:bg-indigo-500 text-white shadow-md shadow-indigo-600/30 transition flex items-center gap-1.5 disabled:opacity-40 disabled:cursor-not-allowed"
          >
            <span>{loading ? "⏳" : "Send"}</span>
            <span>➤</span>
          </button>
        </form>
        <div className="flex items-center justify-between mt-2 text-[10px] text-slate-500">
          <span>💡 Press Enter to send. Agents make real edits to `src/App.tsx` and workspace assets.</span>
          <span>Requires explicit approval before any disk write.</span>
        </div>
      </div>
    </div>
  );
}
