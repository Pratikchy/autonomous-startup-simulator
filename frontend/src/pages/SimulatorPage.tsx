import { useEffect, useState, useCallback } from "react";
import type { AgentType } from "../types";
import { useSim } from "../store/simulationStore";
import TopBar from "../components/TopBar";
import StartupOffice3D from "../components/office3d/StartupOffice3D";
import TasksSidebar from "../components/panels/TasksSidebar";
import HealthSidebar from "../components/panels/HealthSidebar";
import EventFeed from "../components/panels/EventFeed";
import DepartmentDetailPanel from "../components/DepartmentDetailPanel";
import BoardroomView from "../components/debate/BoardroomView";
import BlueprintView from "../components/blueprint/BlueprintView";
import ExecutionDashboard from "../components/execution/ExecutionDashboard";
import AgentWorkHistoryModal from "../components/history/AgentWorkHistoryModal";
import GeneratedAppModal from "../components/app/GeneratedAppModal";

export default function SimulatorPage({
  sseConnected,
  startupId,
}: {
  sseConnected: boolean;
  startupId?: number | null;
}) {
  const startup = useSim((s) => s.startup);
  const load = useSim((s) => s.load);
  const executionState = useSim((s) => s.executionState);
  const loadBlueprint = useSim((s) => s.loadBlueprint);

  const [dept, setDept] = useState<AgentType | null>(null);
  const [boardroom, setBoardroom] = useState(false);
  const [blueprint, setBlueprint] = useState(false);
  const [showExecution, setShowExecution] = useState(false);
  const [historyOpen, setHistoryOpen] = useState(false);
  const [historyAgent, setHistoryAgent] = useState<AgentType | "ALL">("ALL");
  const [appModalOpen, setAppModalOpen] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);

  const openHistory = (agent: AgentType | "ALL" = "ALL") => {
    setHistoryAgent(agent);
    setHistoryOpen(true);
  };

  const handleOpenBoardroom = useCallback(() => {
    setBoardroom(true);
  }, []);

  // Hydrate from backend on mount or when the URL id changes.
  // This is what makes browser-refresh work: the store loads fresh from the DB.
  useEffect(() => {
    if (startupId == null) return;
    if (startup?.id === startupId) return; // already loaded
    setLoadError(null);
    load(startupId).catch((e) => setLoadError((e as Error).message));
  }, [startupId]); // eslint-disable-line react-hooks/exhaustive-deps

  const phase = startup?.currentPhase;

  // Auto-open the execution drawer when the build phase begins. The boardroom
  // debate is surfaced on demand via the BoardroomView modal, not the sidebar.
  useEffect(() => {
    if (phase === "EXECUTION" || executionState?.isRunning) setShowExecution(true);
  }, [phase, executionState?.isRunning]);

  const openBlueprint = async () => {
    await loadBlueprint();
    setBlueprint(true);
  };

  if (loadError) {
    return (
      <div className="h-screen flex flex-col items-center justify-center bg-slate-950 text-slate-400">
        <p className="text-rose-400 font-medium mb-2">Failed to load startup #{startupId}</p>
        <p className="text-sm text-slate-500">{loadError}</p>
        <a href="/" className="mt-4 text-sky-400 hover:text-sky-300 text-sm underline">← Back to home</a>
      </div>
    );
  }

  if (!startup) {
    return (
      <div className="h-screen flex items-center justify-center bg-slate-950 text-slate-400">
        <span className="animate-pulse">Loading startup…</span>
      </div>
    );
  }

  return (
    <div className="h-screen flex flex-col overflow-hidden select-none bg-slate-950">
      <TopBar
        sseConnected={sseConnected}
        onOpenBlueprint={openBlueprint}
        onOpenAgentHistory={() => openHistory("ALL")}
        onOpenGeneratedApp={() => setAppModalOpen(true)}
      />

      <div className="flex-1 flex min-h-0 relative">
        {/* Left sidebar: Task Board */}
        <TasksSidebar />

        {/* Office hero + Execution view */}
        <main className="flex-1 relative flex flex-col min-h-0 overflow-hidden bg-slate-950">
          {/* Main 3D Office Canvas */}
          <div className="flex-1 relative min-h-0">
            <StartupOffice3D
              onSelectDepartment={setDept}
              onOpenBoardroom={handleOpenBoardroom}
            />

            {startup?.simulationCompleted && !showExecution && (
              <button
                onClick={openBlueprint}
                className="absolute bottom-6 left-1/2 -translate-x-1/2 rounded-xl px-4 py-2 font-semibold bg-gradient-to-r from-amber-500 to-amber-600 text-slate-950 shadow-xl hover:brightness-110 transition animate-fade-in-up text-xs z-20"
              >
                📘 Open Startup Blueprint
              </button>
            )}

            {/* Bottom-left quick actions: Agent History + Generated App + Execution Drawer */}
            <div className="absolute bottom-3 left-4 flex items-center gap-2 z-20">
              <button
                onClick={() => openHistory("ALL")}
                className="rounded-lg px-3 py-1.5 text-xs font-bold bg-slate-900/90 backdrop-blur-md text-indigo-300 border border-indigo-500/40 shadow-xl hover:bg-slate-800 hover:text-white transition flex items-center gap-1.5"
                title="Inspect detailed Agent Work History across all 4 departments"
              >
                <span>📜</span>
                <span>Agent Work History</span>
              </button>

              <button
                onClick={() => setAppModalOpen(true)}
                className="rounded-lg px-3 py-1.5 text-xs font-bold bg-slate-900/90 backdrop-blur-md text-sky-300 border border-sky-500/40 shadow-xl hover:bg-slate-800 hover:text-white transition flex items-center gap-1.5"
                title="Open Generated Web Application workspace, file inspector, and preview"
              >
                <span>💻</span>
                <span>Generated App</span>
              </button>

              {(startup?.simulationCompleted || executionState || phase === "EXECUTION") && (
                <button
                  onClick={() => setShowExecution(!showExecution)}
                  className="rounded-lg px-3 py-1.5 text-xs font-bold bg-slate-900/90 backdrop-blur-md text-emerald-400 border border-emerald-500/40 shadow-xl hover:bg-slate-800 transition flex items-center gap-1.5"
                >
                  🚀 {showExecution ? "Hide Execution Drawer" : "Show Execution Drawer"}
                </button>
              )}
            </div>
          </div>

          {/* Collapsible Execution Dashboard Drawer */}
          {showExecution && (
            <div className="h-80 max-h-[50vh] overflow-y-auto p-4 bg-slate-950/95 border-t border-slate-800 shadow-2xl z-30 scroll-slim">
              <ExecutionDashboard />
            </div>
          )}
        </main>

        {/* Right column: health + live feed */}
        <aside className="w-80 shrink-0 flex flex-col gap-3 p-3 min-h-0">
          <HealthSidebar />
          <EventFeed />
        </aside>

        {dept && (
          <DepartmentDetailPanel
            type={dept}
            onClose={() => setDept(null)}
            onOpenWorkHistory={openHistory}
          />
        )}
        {boardroom && <BoardroomView onClose={() => setBoardroom(false)} />}
        {blueprint && <BlueprintView onClose={() => setBlueprint(false)} />}
        {historyOpen && (
          <AgentWorkHistoryModal
            initialAgent={historyAgent}
            onClose={() => setHistoryOpen(false)}
          />
        )}
        {appModalOpen && (
          <GeneratedAppModal onClose={() => setAppModalOpen(false)} />
        )}
      </div>
    </div>
  );
}

