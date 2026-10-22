import { useState, useEffect } from "react";
import { useSim } from "../../store/simulationStore";
import type { GeneratedFileInfo } from "../../types";
import CoworkerChatPanel from "./CoworkerChatPanel";

export default function GeneratedAppModal({ onClose }: { onClose: () => void }) {
  const startup = useSim((s) => s.startup);
  const generatedApp = useSim((s) => s.generatedApp);
  const generatedFiles = useSim((s) => s.generatedFiles);
  const selectedFileContent = useSim((s) => s.selectedFileContent);
  const loadGeneratedApp = useSim((s) => s.loadGeneratedApp);
  const generateApp = useSim((s) => s.generateApp);
  const loadGeneratedFiles = useSim((s) => s.loadGeneratedFiles);
  const selectGeneratedFile = useSim((s) => s.selectGeneratedFile);
  const buildGeneratedApp = useSim((s) => s.buildGeneratedApp);
  const startPreview = useSim((s) => s.startPreview);
  const stopPreview = useSim((s) => s.stopPreview);

  const [activeTab, setActiveTab] = useState<"chat" | "code" | "build" | "preview">("code");
  const [selectedPath, setSelectedPath] = useState<string>("src/App.tsx");
  const [copied, setCopied] = useState(false);
  const [actionLoading, setActionLoading] = useState<string | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  // Load app status and files on mount
  useEffect(() => {
    void loadGeneratedApp();
    void loadGeneratedFiles();
  }, [loadGeneratedApp, loadGeneratedFiles]);

  // Load file content when selected path changes
  useEffect(() => {
    if (selectedPath) {
      void selectGeneratedFile(selectedPath);
    }
  }, [selectedPath, selectGeneratedFile]);

  // Keyboard shortcut to close on ESC
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [onClose]);

  const handleGenerate = async () => {
    setActionLoading("generate");
    setErrorMsg(null);
    try {
      await generateApp();
      await loadGeneratedFiles();
      setSelectedPath("src/App.tsx");
    } catch (e) {
      setErrorMsg((e as Error).message);
    } finally {
      setActionLoading(null);
    }
  };

  const handleBuild = async () => {
    setActionLoading("build");
    setErrorMsg(null);
    try {
      await buildGeneratedApp();
      setActiveTab("build");
    } catch (e) {
      setErrorMsg((e as Error).message);
    } finally {
      setActionLoading(null);
    }
  };

  const handleTogglePreview = async () => {
    setActionLoading("preview");
    setErrorMsg(null);
    try {
      if (generatedApp?.previewUrl) {
        await stopPreview();
      } else {
        await startPreview();
        setActiveTab("preview");
      }
    } catch (e) {
      setErrorMsg((e as Error).message);
    } finally {
      setActionLoading(null);
    }
  };

  const handleCopyCode = () => {
    if (selectedFileContent?.content) {
      navigator.clipboard.writeText(selectedFileContent.content);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  const isGenerated = generatedApp?.status && generatedApp.status !== "UNGENERATED";
  const isReady = generatedApp?.status === "READY" || generatedApp?.buildStatus === "SUCCESS";
  const isPreviewActive = !!generatedApp?.previewUrl;

  const getStatusBadge = () => {
    const status = generatedApp?.status || "UNGENERATED";
    switch (status) {
      case "UNGENERATED":
        return <span className="px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-slate-800 text-slate-400 border border-slate-700">Not Generated Yet</span>;
      case "GENERATING":
        return <span className="px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-amber-500/20 text-amber-300 border border-amber-500/40 animate-pulse">Generating Files…</span>;
      case "GENERATED":
        return <span className="px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-indigo-500/20 text-indigo-300 border border-indigo-500/40">Workspace Generated</span>;
      case "BUILDING":
        return <span className="px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-sky-500/20 text-sky-300 border border-sky-500/40 animate-pulse">Building Bundle…</span>;
      case "READY":
        return <span className="px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-emerald-500/20 text-emerald-300 border border-emerald-500/40">✓ Production Ready</span>;
      case "FAILED":
        return <span className="px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-rose-500/20 text-rose-300 border border-rose-500/40">Build Failed</span>;
      default:
        return null;
    }
  };

  const getFileIcon = (file: GeneratedFileInfo) => {
    if (file.isDirectory) return "📁";
    if (file.path.endsWith(".tsx") || file.path.endsWith(".ts")) return "🔷";
    if (file.path.endsWith(".json")) return "⚙️";
    if (file.path.endsWith(".html")) return "🌐";
    if (file.path.endsWith(".css")) return "🎨";
    if (file.path.endsWith(".md")) return "📘";
    return "📄";
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      data-testid="generated-app-modal"
      className="fixed inset-0 z-50 flex items-center justify-center p-3 md:p-6 bg-black/80 backdrop-blur-md animate-fade-in"
    >
      <div className="bg-slate-950 border border-slate-800 rounded-2xl w-full max-w-6xl h-[92vh] flex flex-col shadow-2xl overflow-hidden">
        {/* Modal Header */}
        <div className="px-6 py-4 border-b border-slate-800/80 bg-slate-900/60 flex flex-wrap items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <div className="h-10 w-10 rounded-xl bg-gradient-to-br from-indigo-500 to-sky-500 flex items-center justify-center text-xl shadow-lg shadow-indigo-500/20">
              💻
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h2 className="text-lg font-bold text-white tracking-wide">
                  Generated Web Application
                </h2>
                <span className="px-2 py-0.5 rounded-full text-[11px] font-bold bg-indigo-500/20 text-indigo-300 border border-indigo-500/30">
                  Startup #{startup?.id}: {startup?.name}
                </span>
                {getStatusBadge()}
              </div>
              <p className="text-xs text-slate-400">
                Isolated React 18 + Vite + TypeScript web application generated deterministically for this venture.
              </p>
            </div>
          </div>

          {/* Quick Action Buttons */}
          <div className="flex items-center gap-2.5">
            <button
              onClick={handleGenerate}
              disabled={actionLoading !== null}
              data-testid="generate-app-btn"
              className="px-3.5 py-1.5 rounded-lg text-xs font-semibold bg-indigo-600 hover:bg-indigo-500 text-white shadow-md shadow-indigo-600/30 transition flex items-center gap-1.5 disabled:opacity-50"
            >
              <span>{actionLoading === "generate" ? "⏳" : "⚡"}</span>
              <span>{isGenerated ? "Re-generate App" : "Generate App"}</span>
            </button>

            {isGenerated && (
              <button
                onClick={handleBuild}
                disabled={actionLoading !== null}
                data-testid="build-app-btn"
                className="px-3.5 py-1.5 rounded-lg text-xs font-semibold bg-slate-800 hover:bg-slate-700 text-slate-200 border border-slate-700 shadow-sm transition flex items-center gap-1.5 disabled:opacity-50"
              >
                <span>{actionLoading === "build" ? "⏳" : "🔨"}</span>
                <span>Run Build</span>
              </button>
            )}

            <button
              onClick={handleTogglePreview}
              disabled={actionLoading !== null || !isGenerated}
              data-testid="preview-app-btn"
              className={`px-3.5 py-1.5 rounded-lg text-xs font-semibold transition flex items-center gap-1.5 disabled:opacity-40 disabled:cursor-not-allowed ${
                isPreviewActive
                  ? "bg-rose-500/20 text-rose-300 border border-rose-500/40 hover:bg-rose-500/30"
                  : isReady
                  ? "bg-emerald-600 hover:bg-emerald-500 text-white shadow-md shadow-emerald-600/30"
                  : "bg-slate-800 text-slate-300 border border-slate-700 hover:bg-slate-700"
              }`}
            >
              <span>{isPreviewActive ? "⏹" : "🌐"}</span>
              <span>{isPreviewActive ? "Stop Preview" : "Preview App"}</span>
            </button>

            <button
              onClick={onClose}
              data-testid="close-app-modal"
              className="h-8 w-8 rounded-lg bg-slate-800/80 hover:bg-slate-700 text-slate-400 hover:text-white flex items-center justify-center transition ml-2"
              title="Close (ESC)"
            >
              ✕
            </button>
          </div>
        </div>

        {/* Security & Workspace Isolation Disclosure */}
        <div className="px-6 py-2 bg-slate-900/40 border-b border-slate-800/40 flex flex-wrap items-center justify-between gap-2 text-[11px] text-slate-400">
          <div className="flex items-center gap-2">
            <span className="text-emerald-400 font-bold">🔒 ISOLATED WORKSPACE:</span>
            <span className="font-mono text-slate-300">./workspaces/startup-{startup?.id}/</span>
            <span className="text-slate-500">(Strict path traversal guard; isolated from backend source)</span>
          </div>
          {isPreviewActive && generatedApp?.previewUrl && (
            <div className="flex items-center gap-2">
              <span className="h-2 w-2 rounded-full bg-emerald-400 animate-pulse" />
              <span className="text-emerald-400 font-bold">Live Preview:</span>
              <a
                href={generatedApp.previewUrl}
                target="_blank"
                rel="noreferrer"
                className="font-mono text-sky-400 hover:underline flex items-center gap-1"
              >
                {generatedApp.previewUrl} ↗
              </a>
            </div>
          )}
        </div>

        {/* Error notification banner */}
        {errorMsg && (
          <div className="px-6 py-2 bg-rose-500/10 border-b border-rose-500/30 text-rose-300 text-xs flex items-center justify-between">
            <span>⚠️ {errorMsg}</span>
            <button onClick={() => setErrorMsg(null)} className="text-rose-400 hover:text-white">✕</button>
          </div>
        )}

        {/* Main Content Area */}
        <div className="flex-1 min-h-0 flex overflow-hidden">
          {/* Left Column: File Explorer */}
          <div className="w-72 shrink-0 border-r border-slate-800/80 bg-slate-950 flex flex-col min-h-0">
            <div className="p-3 border-b border-slate-800/60 flex items-center justify-between text-xs text-slate-400">
              <span className="font-semibold uppercase tracking-wider text-[10px] text-slate-500">Project Files</span>
              <span className="font-mono text-[10px] bg-slate-900 px-1.5 py-0.5 rounded border border-slate-800">
                {generatedFiles.filter((f) => !f.isDirectory).length} files
              </span>
            </div>

            <div className="flex-1 overflow-y-auto scroll-slim p-2 space-y-0.5">
              {!isGenerated ? (
                <div className="p-4 text-center text-slate-500 text-xs space-y-2">
                  <p>No files generated yet.</p>
                  <button
                    onClick={handleGenerate}
                    className="text-xs text-indigo-400 hover:text-indigo-300 font-semibold underline"
                  >
                    Click to generate starter workspace
                  </button>
                </div>
              ) : generatedFiles.length === 0 ? (
                <div className="p-4 text-center text-slate-500 text-xs">
                  Loading files…
                </div>
              ) : (
                generatedFiles.map((file) => {
                  const isSelected = selectedPath === file.path;
                  return (
                    <button
                      key={file.path}
                      onClick={() => !file.isDirectory && setSelectedPath(file.path)}
                      disabled={file.isDirectory}
                      data-testid={`file-item-${file.path}`}
                      className={`w-full text-left px-2.5 py-1.5 rounded-lg text-xs flex items-center justify-between transition ${
                        isSelected
                          ? "bg-indigo-950/40 text-indigo-300 border border-indigo-500/40 font-medium"
                          : file.isDirectory
                          ? "text-slate-500 font-bold cursor-default"
                          : "text-slate-300 hover:bg-slate-900 hover:text-white"
                      }`}
                    >
                      <div className="flex items-center gap-2 truncate">
                        <span>{getFileIcon(file)}</span>
                        <span className="truncate font-mono">{file.path}</span>
                      </div>
                      {!file.isDirectory && (
                        <span className="text-[10px] font-mono text-slate-500 shrink-0 ml-1">
                          {Math.round(file.sizeBytes / 1024 * 10) / 10}k
                        </span>
                      )}
                    </button>
                  );
                })
              )}
            </div>
          </div>

          {/* Right Column: Work Area (Code Viewer, Build Console, Live Preview) */}
          <div className="flex-1 flex flex-col min-h-0 bg-slate-950">
            {/* Tab navigation */}
            <div className="px-4 py-2 border-b border-slate-800/80 bg-slate-900/40 flex items-center justify-between">
              <div className="flex items-center gap-1">
                <button
                  onClick={() => setActiveTab("chat")}
                  data-testid="tab-view-chat"
                  className={`px-3 py-1 rounded-lg text-xs font-semibold transition flex items-center gap-1.5 ${
                    activeTab === "chat"
                      ? "bg-indigo-600 text-white shadow-sm"
                      : "text-slate-400 hover:text-slate-200 hover:bg-slate-800"
                  }`}
                >
                  <span>💬 Coworker Chat</span>
                </button>
                <button
                  onClick={() => setActiveTab("code")}
                  data-testid="tab-view-code"
                  className={`px-3 py-1 rounded-lg text-xs font-semibold transition ${
                    activeTab === "code"
                      ? "bg-indigo-600 text-white shadow-sm"
                      : "text-slate-400 hover:text-slate-200 hover:bg-slate-800"
                  }`}
                >
                  📄 Source Code
                </button>
                <button
                  onClick={() => setActiveTab("build")}
                  data-testid="tab-view-build"
                  className={`px-3 py-1 rounded-lg text-xs font-semibold transition flex items-center gap-1.5 ${
                    activeTab === "build"
                      ? "bg-indigo-600 text-white shadow-sm"
                      : "text-slate-400 hover:text-slate-200 hover:bg-slate-800"
                  }`}
                >
                  <span>🔨 Build Output</span>
                  {generatedApp?.buildStatus === "SUCCESS" && (
                    <span className="h-1.5 w-1.5 rounded-full bg-emerald-400" />
                  )}
                  {generatedApp?.buildStatus === "FAILED" && (
                    <span className="h-1.5 w-1.5 rounded-full bg-rose-400" />
                  )}
                </button>
                <button
                  onClick={() => setActiveTab("preview")}
                  data-testid="tab-view-preview"
                  className={`px-3 py-1 rounded-lg text-xs font-semibold transition flex items-center gap-1.5 ${
                    activeTab === "preview"
                      ? "bg-indigo-600 text-white shadow-sm"
                      : "text-slate-400 hover:text-slate-200 hover:bg-slate-800"
                  }`}
                >
                  <span>🌐 Live Preview</span>
                  {isPreviewActive && (
                    <span className="h-1.5 w-1.5 rounded-full bg-emerald-400 animate-ping" />
                  )}
                </button>
              </div>

              {activeTab === "code" && selectedFileContent && (
                <div className="flex items-center gap-3">
                  <span className="text-[11px] font-mono text-slate-400">
                    {selectedFileContent.name} ({selectedFileContent.sizeBytes} B)
                  </span>
                  <button
                    onClick={handleCopyCode}
                    className="px-2.5 py-1 rounded text-xs bg-slate-800 hover:bg-slate-700 text-slate-300 transition"
                  >
                    {copied ? "✓ Copied" : "Copy"}
                  </button>
                </div>
              )}
            </div>

            {/* Tab 0: Coworker Chat */}
            {activeTab === "chat" && (
              <CoworkerChatPanel
                startupId={startup?.id || 1}
                isAppGenerated={!!isGenerated}
                onAppUpdated={() => {
                  void loadGeneratedFiles();
                  void loadGeneratedApp();
                  if (selectedPath) {
                    void selectGeneratedFile(selectedPath);
                  }
                }}
                onOpenPreview={() => {
                  setActiveTab("preview");
                  if (!isPreviewActive) {
                    void handleTogglePreview();
                  }
                }}
                onSelectFile={(path) => {
                  setSelectedPath(path);
                  setActiveTab("code");
                }}
              />
            )}

            {/* Tab 1: Source Code Viewer */}
            {activeTab === "code" && (
              <div className="flex-1 overflow-auto scroll-slim p-4 bg-slate-950 font-mono text-xs">
                {!isGenerated ? (
                  <div className="h-full flex flex-col items-center justify-center text-slate-500 text-center space-y-3">
                    <span className="text-4xl">⚡</span>
                    <h3 className="text-base font-bold text-slate-300">Ready to Generate Your Startup MVP</h3>
                    <p className="text-xs text-slate-400 max-w-md">
                      Click the &quot;Generate App&quot; button above to create a dedicated, isolated React+TypeScript+Vite starter customized with your venture&apos;s value proposition and interactive controls.
                    </p>
                    <button
                      onClick={handleGenerate}
                      className="px-4 py-2 rounded-xl text-xs font-bold bg-indigo-600 hover:bg-indigo-500 text-white shadow-lg transition"
                    >
                      Generate Application Now
                    </button>
                  </div>
                ) : !selectedFileContent ? (
                  <div className="h-full flex items-center justify-center text-slate-500">
                    Select a file on the left to inspect its source code.
                  </div>
                ) : (
                  <div className="bg-slate-900/60 rounded-xl p-4 border border-slate-800/80 overflow-x-auto text-slate-200 leading-relaxed whitespace-pre font-mono">
                    {selectedFileContent.content}
                  </div>
                )}
              </div>
            )}

            {/* Tab 2: Build & Diagnostics Log */}
            {activeTab === "build" && (
              <div className="flex-1 flex flex-col min-h-0 p-4 space-y-3 bg-slate-950">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <span className="text-xs font-semibold text-slate-300">Build Status:</span>
                    <span className={`px-2 py-0.5 rounded text-[11px] font-bold ${
                      generatedApp?.buildStatus === "SUCCESS"
                        ? "bg-emerald-500/20 text-emerald-400 border border-emerald-500/30"
                        : generatedApp?.buildStatus === "FAILED"
                        ? "bg-rose-500/20 text-rose-400 border border-rose-500/30"
                        : "bg-slate-800 text-slate-400"
                    }`}>
                      {generatedApp?.buildStatus || "NOT_BUILT"}
                    </span>
                  </div>
                  <button
                    onClick={handleBuild}
                    disabled={actionLoading !== null}
                    className="px-3 py-1 rounded-lg text-xs font-semibold bg-slate-800 hover:bg-slate-700 text-slate-200 border border-slate-700 transition"
                  >
                    {actionLoading === "build" ? "Building…" : "Re-run Build"}
                  </button>
                </div>

                <div className="flex-1 bg-black/90 p-4 rounded-xl border border-slate-800 font-mono text-xs text-slate-300 overflow-auto scroll-slim whitespace-pre leading-relaxed">
                  {generatedApp?.buildOutput || "No build executed yet. Click 'Run Build' above to compile and bundle the production assets."}
                </div>
              </div>
            )}

            {/* Tab 3: Live Preview */}
            {activeTab === "preview" && (
              <div className="flex-1 flex flex-col min-h-0 bg-slate-950">
                {actionLoading === "preview" ? (
                  <div className="h-full flex flex-col items-center justify-center text-center p-6 space-y-3">
                    <div className="h-10 w-10 border-4 border-indigo-500 border-t-transparent rounded-full animate-spin" />
                    <h3 className="text-base font-bold text-white">Starting Live Preview…</h3>
                    <p className="text-xs text-slate-400 max-w-md">
                      Compiling production assets and launching the isolated preview server.
                    </p>
                  </div>
                ) : isPreviewActive && generatedApp?.previewUrl ? (
                  <div className="flex-1 flex flex-col min-h-0">
                    <div className="px-4 py-2 bg-slate-900/60 border-b border-slate-800/60 flex items-center justify-between text-xs">
                      <div className="flex items-center gap-2">
                        <span className="h-2 w-2 rounded-full bg-emerald-400 animate-pulse" />
                        <span className="text-slate-300 font-mono">{generatedApp.previewUrl}</span>
                      </div>
                      <div className="flex items-center gap-2">
                        <a
                          href={generatedApp.previewUrl}
                          target="_blank"
                          rel="noreferrer"
                          className="px-2.5 py-1 rounded bg-indigo-600 hover:bg-indigo-500 text-white font-semibold transition flex items-center gap-1"
                        >
                          <span>Open in New Tab</span>
                          <span>↗</span>
                        </a>
                        <button
                          onClick={handleTogglePreview}
                          className="px-2.5 py-1 rounded bg-slate-800 hover:bg-slate-700 text-slate-300 transition"
                        >
                          Stop Server
                        </button>
                      </div>
                    </div>
                    <iframe
                      src={generatedApp.previewUrl}
                      title="Generated App Live Preview"
                      className="flex-1 w-full border-none bg-slate-950"
                    />
                  </div>
                ) : (
                  <div className="h-full flex flex-col items-center justify-center text-center p-6 space-y-4">
                    <span className="text-4xl">🌐</span>
                    <h3 className="text-base font-bold text-white">Live App Preview Server</h3>
                    <p className="text-xs text-slate-400 max-w-md">
                      Start the isolated Vite preview server to interactively test your generated web application in real-time.
                    </p>
                    <button
                      onClick={handleTogglePreview}
                      disabled={actionLoading !== null || !isGenerated}
                      className="px-4 py-2 rounded-xl text-xs font-bold bg-emerald-600 hover:bg-emerald-500 text-white shadow-lg transition flex items-center gap-2 disabled:opacity-50"
                    >
                      <span>🚀</span>
                      <span>{actionLoading === "preview" ? "Starting Preview…" : "Start Preview Server"}</span>
                    </button>
                  </div>
                )}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
