import { memo, useState } from "react";
import { Html } from "@react-three/drei";
import type { AgentState, AgentType } from "../../types";

interface DepartmentStatusBadge3DProps {
  department: AgentType | "BOARDROOM";
  position: [number, number, number];
  state: AgentState;
  taskTitle?: string | null;
  progress?: number | null;
  blockerReason?: string | null;
  isActive: boolean;
  onSelect?: () => void;
}

const DEPT_BADGE_CONFIG: Record<
  AgentType | "BOARDROOM",
  { title: string; shortTitle: string; icon: string; accentColor: string; borderColor: string }
> = {
  CEO: {
    title: "CEO STRATEGY",
    shortTitle: "CEO",
    icon: "🎯",
    accentColor: "text-amber-400",
    borderColor: "border-amber-500/50",
  },
  DEVELOPMENT: {
    title: "DEVELOPMENT",
    shortTitle: "DEV",
    icon: "⚡",
    accentColor: "text-cyan-400",
    borderColor: "border-cyan-500/50",
  },
  MARKETING: {
    title: "MARKETING",
    shortTitle: "MKT",
    icon: "📈",
    accentColor: "text-pink-400",
    borderColor: "border-pink-500/50",
  },
  FINANCE: {
    title: "FINANCE",
    shortTitle: "FIN",
    icon: "💵",
    accentColor: "text-emerald-400",
    borderColor: "border-emerald-500/50",
  },
  BOARDROOM: {
    title: "BOARDROOM",
    shortTitle: "BOARD",
    icon: "⚖️",
    accentColor: "text-purple-400",
    borderColor: "border-purple-500/50",
  },
};

function DepartmentStatusBadge3DComponent({
  department,
  position,
  state,
  taskTitle,
  progress,
  blockerReason,
  isActive,
  onSelect,
}: DepartmentStatusBadge3DProps) {
  const config = DEPT_BADGE_CONFIG[department];
  const [isHovered, setIsHovered] = useState(false);

  // LEVEL 2 State indicator pill
  const renderStatusIndicator = () => {
    if (state === "THINKING") {
      return (
        <span className="flex items-center gap-1 text-[10px] text-amber-300 font-mono">
          <span className="w-1.5 h-1.5 rounded-full bg-amber-400 animate-ping" />
          <span>THINKING • • •</span>
        </span>
      );
    }
    if (state === "WORKING" || state === "DISCUSSING" || state === "DECIDING") {
      return (
        <span className="flex items-center gap-1 text-[10px] text-emerald-400 font-mono">
          <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" />
          <span>WORKING</span>
        </span>
      );
    }
    if (state === "BLOCKED") {
      return (
        <span className="flex items-center gap-1 text-[10px] text-rose-400 font-mono font-bold">
          <span className="w-1.5 h-1.5 rounded-full bg-rose-500 animate-bounce" />
          <span>⚠️ BLOCKED</span>
        </span>
      );
    }
    if (state === "COMPLETED") {
      return (
        <span className="flex items-center gap-1 text-[10px] text-cyan-300 font-mono">
          <span>✔ COMPLETED</span>
        </span>
      );
    }
    return (
      <span className="text-slate-400 font-mono text-[9.5px]">
        <span>IDLE</span>
      </span>
    );
  };

  const showDetails = isActive || isHovered;

  return (
    <Html
      position={position}
      center
      distanceFactor={11.5}
      zIndexRange={[10, 0]}
    >
      <div
        onMouseEnter={() => setIsHovered(true)}
        onMouseLeave={() => setIsHovered(false)}
        onClick={onSelect}
        className={`bg-slate-950/95 px-2.5 py-1 rounded-lg border shadow-xl transition-colors duration-150 cursor-pointer select-none ${
          config.borderColor
        } ${
          isActive
            ? "ring-2 ring-cyan-400/50 scale-105 bg-slate-900"
            : "opacity-95 hover:opacity-100 hover:scale-105"
        } ${showDetails ? "max-w-[180px]" : "max-w-[130px]"}`}
      >
        {/* LEVEL 1 (Room identity) & LEVEL 2 (State) */}
        <div className="flex items-center justify-between gap-2">
          <div className="flex items-center gap-1 font-bold text-[11px] text-white whitespace-nowrap">
            <span>{config.icon}</span>
            <span className={config.accentColor}>
              {showDetails ? config.title : config.shortTitle}
            </span>
          </div>
          {renderStatusIndicator()}
        </div>

        {/* LEVEL 3 Detailed Info (Task title, progress, blocker details) */}
        {showDetails && (
          <div className="mt-1 pt-1 border-t border-slate-800/80 animate-fade-in">
            {taskTitle && (
              <div className="text-[10px] text-slate-200 font-medium truncate mb-1" title={taskTitle}>
                "{taskTitle}"
              </div>
            )}

            {/* Compact Blocker Detail */}
            {state === "BLOCKED" && blockerReason && (
              <div className="text-[9px] text-rose-300 font-mono bg-rose-950/80 p-1 rounded border border-rose-800/50 mb-1 max-w-[180px] leading-tight break-words">
                {blockerReason}
              </div>
            )}

            {/* Progress Bar */}
            {typeof progress === "number" && progress > 0 && progress < 100 && (
              <div className="w-full bg-slate-800 h-1 rounded-full overflow-hidden mt-0.5">
                <div
                  className="bg-gradient-to-r from-cyan-500 to-emerald-400 h-full transition-all duration-300"
                  style={{ width: `${progress}%` }}
                />
              </div>
            )}
          </div>
        )}
      </div>
    </Html>
  );
}

export default memo(DepartmentStatusBadge3DComponent);
