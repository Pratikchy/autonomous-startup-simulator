import { useEffect, useState } from "react";
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

export default function SimulatorPage({ sseConnected }: { sseConnected: boolean }) {
  const startup = useSim((s) => s.startup);
  const executionState = useSim((s) => s.executionState);
  const loadBlueprint = useSim((s) => s.loadBlueprint);

  const [dept, setDept] = useState<AgentType | null>(null);
  const [boardroom, setBoardroom] = useState(false);
  const [blueprint, setBlueprint] = useState(false);
  const [showExecution, setShowExecution] = useState(false);

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

  return (
    <div className="h-screen flex flex-col overflow-hidden select-none bg-slate-950">
      <TopBar sseConnected={sseConnected} onOpenBlueprint={openBlueprint} />

      <div className="flex-1 flex min-h-0 relative">
        {/* Left sidebar: Task Board */}
        <TasksSidebar />

        {/* Office hero + Execution view */}
        <main className="flex-1 relative flex flex-col min-h-0 overflow-hidden bg-slate-950">
          {/* Main 3D Office Canvas */}
          <div className="flex-1 relative min-h-0">
            <StartupOffice3D
              onSelectDepartment={setDept}
              onOpenBoardroom={() => setBoardroom(true)}
            />

            {startup?.simulationCompleted && !showExecution && (
              <button
                onClick={openBlueprint}
                className="absolute bottom-6 left-1/2 -translate-x-1/2 rounded-xl px-4 py-2 font-semibold bg-gradient-to-r from-amber-500 to-amber-600 text-slate-950 shadow-xl hover:brightness-110 transition animate-fade-in-up text-xs z-20"
              >
                📘 Open Startup Blueprint
              </button>
            )}

            {/* Execution Dashboard Overlay Toggle */}
            {(startup?.simulationCompleted || executionState || phase === "EXECUTION") && (
              <button
                onClick={() => setShowExecution(!showExecution)}
                className="absolute bottom-3 left-4 rounded-lg px-3 py-1.5 text-xs font-bold bg-slate-900/90 backdrop-blur-md text-emerald-400 border border-emerald-500/40 shadow-xl hover:bg-slate-800 transition z-20 flex items-center gap-1.5"
              >
                🚀 {showExecution ? "Hide Execution Drawer" : "Show Execution Drawer"}
              </button>
            )}
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
          <DepartmentDetailPanel type={dept} onClose={() => setDept(null)} />
        )}
        {boardroom && <BoardroomView onClose={() => setBoardroom(false)} />}
        {blueprint && <BlueprintView onClose={() => setBlueprint(false)} />}
      </div>
    </div>
  );
}
