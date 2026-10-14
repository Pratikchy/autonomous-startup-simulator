import { useMemo, memo } from "react";
import { useSim } from "../../store/simulationStore";
import type { AgentState, AgentType } from "../../types";
import { EMPLOYEES } from "./officeLayout";
import CEOOffice3D from "./CEOOffice3D";
import DevelopmentOffice3D from "./DevelopmentOffice3D";
import MarketingOffice3D from "./MarketingOffice3D";
import FinanceOffice3D from "./FinanceOffice3D";
import Boardroom3D from "./Boardroom3D";
import Employee3D, { type EmployeeActivity } from "./Employee3D";
import { Plant3D } from "./OfficeFurniture3D";
import OrchestratorCore3D from "./OrchestratorCore3D";
import CommunicationSignal3D, { type ActiveSignal } from "./CommunicationSignal3D";
import DepartmentStatusBadge3D from "./DepartmentStatusBadge3D";

/* ═══════════════════════════════════════════════════════════════════
   Glass Partition Wall with Dark Metal Frame, Door Handles & Frosted Accent
   (Optimized standard material — NO transmission pass)
   ═══════════════════════════════════════════════════════════════════ */
const GlassWall = memo(function GlassWall({
  position,
  rotation = [0, 0, 0] as [number, number, number],
  width,
  height = 2.3,
  doorWidth = 1.0,
}: {
  position: [number, number, number];
  rotation?: [number, number, number];
  width: number;
  height?: number;
  doorWidth?: number;
}) {
  const panelW = (width - doorWidth) / 2;
  const halfDoor = doorWidth / 2;
  return (
    <group position={position} rotation={rotation}>
      {/* Left glass panel — Crystal Clear */}
      <mesh position={[-(halfDoor + panelW / 2), height / 2, 0]}>
        <boxGeometry args={[panelW, height - 0.06, 0.02]} />
        <meshStandardMaterial
          color="#e0f2fe"
          transparent
          opacity={0.14}
          roughness={0.04}
          metalness={0.1}
        />
      </mesh>

      {/* Right glass panel — Crystal Clear */}
      <mesh position={[(halfDoor + panelW / 2), height / 2, 0]}>
        <boxGeometry args={[panelW, height - 0.06, 0.02]} />
        <meshStandardMaterial
          color="#e0f2fe"
          transparent
          opacity={0.14}
          roughness={0.04}
          metalness={0.1}
        />
      </mesh>

      {/* Top horizontal frame beam */}
      <mesh position={[0, height, 0]} castShadow>
        <boxGeometry args={[width + 0.02, 0.04, 0.035]} />
        <meshStandardMaterial color="#1e293b" roughness={0.3} metalness={0.8} />
      </mesh>

      {/* Bottom frame rail */}
      <mesh position={[0, 0.015, 0]}>
        <boxGeometry args={[width + 0.02, 0.03, 0.035]} />
        <meshStandardMaterial color="#1e293b" roughness={0.3} metalness={0.8} />
      </mesh>

      {/* Door header beam */}
      <mesh position={[0, height - 0.02, 0]} castShadow>
        <boxGeometry args={[doorWidth + 0.04, 0.04, 0.035]} />
        <meshStandardMaterial color="#1e293b" roughness={0.3} metalness={0.8} />
      </mesh>

      {/* Door Frame Posts & Handle */}
      <mesh position={[-halfDoor, height / 2, 0]} castShadow>
        <boxGeometry args={[0.03, height, 0.035]} />
        <meshStandardMaterial color="#1e293b" roughness={0.3} metalness={0.8} />
      </mesh>
      <mesh position={[halfDoor, height / 2, 0]} castShadow>
        <boxGeometry args={[0.03, height, 0.035]} />
        <meshStandardMaterial color="#1e293b" roughness={0.3} metalness={0.8} />
      </mesh>
      {/* Sleek Stainless Door Handle */}
      <mesh position={[halfDoor - 0.05, 1.0, 0.025]} castShadow>
        <boxGeometry args={[0.018, 0.2, 0.035]} />
        <meshStandardMaterial color="#94a3b8" roughness={0.2} metalness={0.9} />
      </mesh>

      {/* Outer vertical mullion posts */}
      {[-width / 2, width / 2].map((x, i) => (
        <mesh key={i} position={[x, height / 2, 0]} castShadow>
          <boxGeometry args={[0.035, height, 0.035]} />
          <meshStandardMaterial color="#1e293b" roughness={0.3} metalness={0.8} />
        </mesh>
      ))}
    </group>
  );
});

/* ═══════════════════════════════════════════════════════════════════
   Architectural Column (at partition junctions)
   ═══════════════════════════════════════════════════════════════════ */
const ArchColumn = memo(function ArchColumn({ position }: { position: [number, number, number] }) {
  return (
    <group position={position}>
      {/* Column shaft */}
      <mesh position={[0, 1.15, 0]} castShadow>
        <boxGeometry args={[0.16, 2.3, 0.16]} />
        <meshStandardMaterial color="#64748b" roughness={0.3} metalness={0.6} />
      </mesh>
      {/* Column base molding */}
      <mesh position={[0, 0.035, 0]} castShadow>
        <boxGeometry args={[0.22, 0.07, 0.22]} />
        <meshStandardMaterial color="#475569" roughness={0.3} metalness={0.5} />
      </mesh>
      {/* Column capital */}
      <mesh position={[0, 2.28, 0]}>
        <boxGeometry args={[0.2, 0.04, 0.2]} />
        <meshStandardMaterial color="#475569" roughness={0.3} metalness={0.5} />
      </mesh>
    </group>
  );
});

/* ═══════════════════════════════════════════════════════════════════
   Outer Wall Segment with Modern Silicon Valley Window Architecture
   ═══════════════════════════════════════════════════════════════════ */
const OuterWall = memo(function OuterWall({
  position,
  rotation = [0, 0, 0] as [number, number, number],
  width,
  height = 2.5,
  hasWindows = false,
}: {
  position: [number, number, number];
  rotation?: [number, number, number];
  width: number;
  height?: number;
  hasWindows?: boolean;
}) {
  if (!hasWindows) {
    return (
      <group position={position} rotation={rotation}>
        <mesh position={[0, height / 2, 0]} castShadow receiveShadow>
          <boxGeometry args={[width, height, 0.12]} />
          <meshStandardMaterial color="#ebe5dc" roughness={0.65} metalness={0.02} />
        </mesh>
        <mesh position={[0, 0.06, 0.065]} castShadow>
          <boxGeometry args={[width, 0.12, 0.02]} />
          <meshStandardMaterial color="#3d2817" roughness={0.4} metalness={0.1} />
        </mesh>
      </group>
    );
  }

  // Large modern floor-to-ceiling office window bays
  const bayCount = Math.floor(width / 3.8);
  const bayWidth = 3.2;
  const windowHeight = 1.8;
  const windowBottom = 0.45;

  return (
    <group position={position} rotation={rotation}>
      {/* Main architectural wall body */}
      <mesh position={[0, height / 2, 0]} castShadow receiveShadow>
        <boxGeometry args={[width, height, 0.12]} />
        <meshStandardMaterial color="#ebe5dc" roughness={0.65} metalness={0.02} />
      </mesh>

      {/* Modern Window Bays */}
      {Array.from({ length: bayCount }, (_, i) => {
        const x = (i - (bayCount - 1) / 2) * (width / bayCount);
        return (
          <group key={i} position={[x, windowBottom + windowHeight / 2, 0.065]}>
            {/* Window Glass Pane */}
            <mesh>
              <boxGeometry args={[bayWidth, windowHeight, 0.02]} />
              <meshStandardMaterial
                color="#546e7a"
                transparent
                opacity={0.32}
                roughness={0.08}
                metalness={0.4}
              />
            </mesh>
            {/* Dark Aluminum Outer Window Frame */}
            <mesh>
              <boxGeometry args={[bayWidth + 0.08, windowHeight + 0.08, 0.03]} />
              <meshStandardMaterial color="#1e293b" roughness={0.3} metalness={0.8} />
            </mesh>
            {/* Vertical Mullions */}
            {[-bayWidth / 3, bayWidth / 3].map((mX) => (
              <mesh key={mX} position={[mX, 0, 0.015]}>
                <boxGeometry args={[0.04, windowHeight, 0.03]} />
                <meshStandardMaterial color="#334155" roughness={0.3} metalness={0.8} />
              </mesh>
            ))}
            {/* Horizontal Transom Rail */}
            <mesh position={[0, windowHeight * 0.25, 0.015]}>
              <boxGeometry args={[bayWidth, 0.035, 0.03]} />
              <meshStandardMaterial color="#334155" roughness={0.3} metalness={0.8} />
            </mesh>
          </group>
        );
      })}

      {/* Walnut Baseboard Trim */}
      <mesh position={[0, 0.06, 0.065]} castShadow>
        <boxGeometry args={[width, 0.12, 0.02]} />
        <meshStandardMaterial color="#3d2817" roughness={0.4} metalness={0.1} />
      </mesh>
    </group>
  );
});

/* ═══════════════════════════════════════════════════════════════════
   MAIN OFFICE SCENE — AI OPERATIONS HQ
   ═══════════════════════════════════════════════════════════════════ */
interface OfficeSceneProps {
  onSelectDepartment: (dept: AgentType) => void;
  onOpenBoardroom: () => void;
}

function OfficeSceneComponent({
  onSelectDepartment,
  onOpenBoardroom,
}: OfficeSceneProps) {
  const startup = useSim((s) => s.startup);
  const agents = useSim((s) => s.agents);
  const debates = useSim((s) => s.debates);
  const tasks = useSim((s) => s.tasks);
  const executionState = useSim((s) => s.executionState);
  const events = useSim((s) => s.events);

  const phase = startup?.currentPhase;
  const currentDebate = debates.length > 0 ? debates[debates.length - 1] : null;
  const inDebate = phase === "DEBATE" || (currentDebate !== null && currentDebate.status === "OPEN");

  const lastMessage =
    currentDebate && currentDebate.messages.length > 0
      ? currentDebate.messages[currentDebate.messages.length - 1]
      : null;
  const activeSpeaker = lastMessage ? lastMessage.agentLabel : null;
  const currentRound = lastMessage ? lastMessage.debateRound : null;

  // Active department derived from agent working state / active execution task / debate
  const activeAgent = useMemo<AgentType | "BOARDROOM" | null>(() => {
    if (inDebate) return "BOARDROOM";
    const activeWorkingAgent = agents.find(
      (a) => a.state === "WORKING" || a.state === "THINKING" || a.state === "DISCUSSING" || a.state === "DECIDING"
    );
    if (activeWorkingAgent) return activeWorkingAgent.type;

    if (executionState && executionState.tasks) {
      const runningTask = executionState.tasks.find((t) => t.status === "IN_PROGRESS");
      if (runningTask) return runningTask.department;
    }
    return null;
  }, [inDebate, agents, executionState]);

  // Orchestration status label derived from simulation state
  const orchestrationStatus = useMemo(() => {
    if (inDebate) return "BOARDROOM DEBATE";
    if (activeAgent) return `EXECUTING: ${activeAgent}`;
    if (phase) return `PHASE: ${phase}`;
    return "ORCHESTRATOR READY";
  }, [inDebate, activeAgent, phase]);

  // Helper to extract active task title for each room
  const getDepartmentTaskInfo = (dept: AgentType | "BOARDROOM") => {
    if (dept === "BOARDROOM") {
      return {
        state: (inDebate ? "WORKING" : "IDLE") as AgentState,
        taskTitle: inDebate ? `Debate Round ${currentRound || 1}: ${currentDebate?.topic || "Strategy"}` : null,
        progress: null,
        blockerReason: null,
      };
    }

    const agent = agents.find((a) => a.type === dept);
    const agentState: AgentState = agent ? agent.state : "IDLE";

    // Check execution tasks first
    if (executionState && executionState.tasks) {
      const activeTask = executionState.tasks.find(
        (t) => t.department === dept && (t.status === "IN_PROGRESS" || t.status === "BLOCKED")
      );
      if (activeTask) {
        return {
          state: (activeTask.status === "BLOCKED" ? "BLOCKED" : agentState) as AgentState,
          taskTitle: activeTask.title,
          progress: activeTask.progress,
          blockerReason: activeTask.blockerReason || null,
        };
      }
    }

    // Check agent tasks second
    const agentTask = tasks.find(
      (t) => t.agentType === dept && (t.status === "IN_PROGRESS" || t.status === "BLOCKED")
    );
    if (agentTask) {
      return {
        state: (agentTask.status === "BLOCKED" ? "BLOCKED" : agentState) as AgentState,
        taskTitle: agentTask.title,
        progress: agentTask.progress,
        blockerReason: null,
      };
    }

    return {
      state: agentState,
      taskTitle: agent?.currentActivity || null,
      progress: null,
      blockerReason: null,
    };
  };

  // Derive active inter-agent communication signals
  const activeSignals = useMemo<ActiveSignal[]>(() => {
    const list: ActiveSignal[] = [];

    // Debate speech communication signal
    if (inDebate && lastMessage && lastMessage.agentType) {
      const target: AgentType | "BOARDROOM" = lastMessage.targetAgent || "BOARDROOM";
      list.push({
        id: `msg-${lastMessage.id}`,
        from: lastMessage.agentType,
        to: target,
        label: `${lastMessage.agentType} → ${target}`,
        startTime: 0,
      });
    }

    // Event-based communication signals (last 2 events)
    const recentEvents = events.slice(-3);
    recentEvents.forEach((ev) => {
      if (ev.type === "TASK_ASSIGNED" || ev.type === "AGENT_RESPONDED" || ev.type === "AGENT_MESSAGE_CREATED") {
        let fromDept: AgentType | "BOARDROOM" = "CEO";
        let toDept: AgentType | "BOARDROOM" = "DEVELOPMENT";
        if (ev.message.includes("DEVELOPMENT")) toDept = "DEVELOPMENT";
        else if (ev.message.includes("MARKETING")) toDept = "MARKETING";
        else if (ev.message.includes("FINANCE")) toDept = "FINANCE";

        list.push({
          id: `ev-${ev.id}`,
          from: fromDept,
          to: toDept,
          label: ev.type,
          startTime: 0,
        });
      }
    });

    return list;
  }, [inDebate, lastMessage, events]);

  // Map employee animation activity based on store execution state
  const getEmployeeActivity = (dept: AgentType): EmployeeActivity => {
    const agent = agents.find((a) => a.type === dept);
    if (!agent) return "TYPING";
    if (inDebate || agent.state === "MEETING") return "DEBATING";
    if (agent.state === "BLOCKED") return "BLOCKED";
    if (agent.state === "WALKING") return "WALKING";
    if (agent.state === "WORKING" || agent.state === "THINKING") return "TYPING";
    if (agent.state === "DISCUSSING") return "TALKING";
    if (agent.state === "DECIDING") return "PRESENTING";
    if (agent.state === "IDLE" || agent.state === "WAITING") return "IDLE";
    if (agent.state === "COMPLETED") return "READING";
    return "TYPING";
  };

  const ceoTask = getDepartmentTaskInfo("CEO");
  const devTask = getDepartmentTaskInfo("DEVELOPMENT");
  const mktTask = getDepartmentTaskInfo("MARKETING");
  const finTask = getDepartmentTaskInfo("FINANCE");
  const boardroomTask = getDepartmentTaskInfo("BOARDROOM");

  return (
    <group>
      {/* ═══════════════════════════════════════════════════════════
          1. OPTIMIZED LIGHTING HIERARCHY
          Key directional sun (1024 shadow map) + warm interior ambient
          ═══════════════════════════════════════════════════════════ */}

      {/* Warm ambient base fill — neutralized, no amber cast */}
      <ambientLight intensity={0.72} color="#f6f0e8" />

      {/* Hemisphere sky/ground bounce — muted sage/charcoal */}
      <hemisphereLight
        color="#eae6de"
        groundColor="#4a6355"
        intensity={0.32}
      />

      {/* Key directional sun — soft, very slight shadow falloff */}
      <directionalLight
        position={[16, 22, 14]}
        intensity={1.15}
        color="#fff9ef"
        castShadow
        shadow-mapSize-width={1024}
        shadow-mapSize-height={1024}
        shadow-camera-near={0.5}
        shadow-camera-far={50}
        shadow-camera-left={-14}
        shadow-camera-right={14}
        shadow-camera-top={14}
        shadow-camera-bottom={-14}
        shadow-bias={-0.0007}
      />

      {/* Cool architectural fill from opposite wall */}
      <directionalLight position={[-12, 14, -10]} intensity={0.22} color="#e8ecf2" />

      {/* Warm interior fill — reduced */}
      <directionalLight position={[0, 10, -16]} intensity={0.16} color="#efe8dc" />

      {/* Overhead fill point lights — dimmer, warmer */}
      <pointLight position={[0, 5, 0]} color="#fff6ea" intensity={0.55} distance={18} />
      <pointLight position={[-5, 4, -3]} color="#f2eadd" intensity={0.22} distance={10} />
      <pointLight position={[5, 4, 3]} color="#f2eadd" intensity={0.22} distance={10} />

      {/* ═══════════════════════════════════════════════════════════
          2. ENVIRONMENT BACKDROP
          ═══════════════════════════════════════════════════════════ */}
      <mesh position={[0, -0.1, 0]} rotation={[-Math.PI / 2, 0, 0]} receiveShadow>
        <planeGeometry args={[80, 80]} />
        <meshStandardMaterial color="#161a2b" roughness={0.95} />
      </mesh>

      {/* ═══════════════════════════════════════════════════════════
          3. MAIN FLOOR — WARM OAK WOOD SURFACE
          ═══════════════════════════════════════════════════════════ */}
      <mesh position={[0, -0.03, 0]} castShadow receiveShadow>
        <boxGeometry args={[16.8, 0.06, 13.8]} />
        <meshStandardMaterial color="#5c4033" roughness={0.5} metalness={0.1} />
      </mesh>

      {/* Main warm oak wood floor — walnut, matte/satin */}
      <mesh position={[0, 0.005, 0]} receiveShadow>
        <boxGeometry args={[16.4, 0.01, 13.4]} />
        <meshStandardMaterial color="#7e5a3a" roughness={0.58} metalness={0.06} />
      </mesh>

      {/* Wood plank accent lines — subtle walnut grain */}
      {[-6.4, -4.8, -3.2, -1.6, 0, 1.6, 3.2, 4.8, 6.4].map((x) => (
        <mesh key={x} position={[x, 0.012, 0]}>
          <boxGeometry args={[0.015, 0.002, 13.2]} />
          <meshStandardMaterial color="#5a3f27" roughness={0.72} />
        </mesh>
      ))}

      {/* Cross-plank accents — very restrained */}
      {[-5.2, -2.6, 0, 2.6, 5.2].map((z) => (
        <mesh key={z} position={[0, 0.013, z]}>
          <boxGeometry args={[16.2, 0.001, 0.01]} />
          <meshStandardMaterial color="#6b4d33" roughness={0.78} />
        </mesh>
      ))}

      {/* Circulation Pathway Accent Strips */}
      <mesh position={[0, 0.014, -2.15]}>
        <boxGeometry args={[15.8, 0.002, 0.06]} />
        <meshStandardMaterial color="#1e293b" roughness={0.5} />
      </mesh>
      <mesh position={[0, 0.014, 2.15]}>
        <boxGeometry args={[15.8, 0.002, 0.06]} />
        <meshStandardMaterial color="#1e293b" roughness={0.5} />
      </mesh>

      {/* ─── Department carpet zone accents — muted sage/charcoal, restrained ─── */}
      <mesh position={[0, 0.014, -4.2]} receiveShadow>
        <boxGeometry args={[4.6, 0.004, 3.0]} />
        <meshStandardMaterial color="#9fb1a3" roughness={0.86} />
      </mesh>
      <mesh position={[0, 0.014, 0]} receiveShadow>
        <boxGeometry args={[5.2, 0.004, 3.4]} />
        <meshStandardMaterial color="#556468" roughness={0.9} />
      </mesh>
      <mesh position={[5.2, 0.014, 0]} receiveShadow>
        <boxGeometry args={[4.0, 0.004, 3.4]} />
        <meshStandardMaterial color="#5e6a76" roughness={0.88} />
      </mesh>
      <mesh position={[-5.2, 0.014, 0]} receiveShadow>
        <boxGeometry args={[4.0, 0.004, 3.4]} />
        <meshStandardMaterial color="#6b6a7a" roughness={0.88} />
      </mesh>
      <mesh position={[0, 0.014, 4.2]} receiveShadow>
        <boxGeometry args={[4.6, 0.004, 3.0]} />
        <meshStandardMaterial color="#7a8c82" roughness={0.88} />
      </mesh>

      {/* ═══════════════════════════════════════════════════════════
          4. OUTER WALLS
          ═══════════════════════════════════════════════════════════ */}
      <OuterWall
        position={[0, 0, -6.45]}
        width={16.4}
        height={2.5}
        hasWindows
      />
      <OuterWall
        position={[-8.0, 0, 0]}
        rotation={[0, Math.PI / 2, 0]}
        width={13.0}
        height={2.5}
        hasWindows
      />

      {/* SOUTH wall (low sill) */}
      <mesh position={[0, 0.25, 6.45]} castShadow receiveShadow>
        <boxGeometry args={[16.4, 0.5, 0.1]} />
        <meshStandardMaterial color="#ebe5dc" roughness={0.6} metalness={0.05} />
      </mesh>
      <mesh position={[0, 0.7, 6.45]}>
        <boxGeometry args={[16.4, 0.4, 0.03]} />
        <meshStandardMaterial
          color="#d8e4f0"
          transparent
          opacity={0.18}
          roughness={0.05}
          metalness={0.1}
        />
      </mesh>

      {/* EAST wall (low sill) */}
      <mesh position={[7.85, 0.25, 0]} castShadow receiveShadow>
        <boxGeometry args={[0.1, 0.5, 13.4]} />
        <meshStandardMaterial color="#ebe5dc" roughness={0.6} metalness={0.05} />
      </mesh>
      <mesh position={[7.85, 0.7, 0]}>
        <boxGeometry args={[0.03, 0.4, 13.4]} />
        <meshStandardMaterial
          color="#d8e4f0"
          transparent
          opacity={0.18}
          roughness={0.05}
          metalness={0.1}
        />
      </mesh>

      {/* ═══════════════════════════════════════════════════════════
          5. INTERIOR GLASS PARTITION SYSTEM
          ═══════════════════════════════════════════════════════════ */}
      <GlassWall position={[0, 0, -2.15]} width={5.2} height={2.3} doorWidth={1.1} />
      <GlassWall
        position={[2.95, 0, 0]}
        rotation={[0, Math.PI / 2, 0]}
        width={4.0}
        height={2.3}
        doorWidth={1.1}
      />
      <GlassWall
        position={[-2.95, 0, 0]}
        rotation={[0, Math.PI / 2, 0]}
        width={4.0}
        height={2.3}
        doorWidth={1.1}
      />
      <GlassWall position={[0, 0, 2.15]} width={5.2} height={2.3} doorWidth={1.1} />
      <GlassWall position={[5.2, 0, -2.15]} width={4.4} height={2.3} doorWidth={0.9} />
      <GlassWall position={[5.2, 0, 2.15]} width={4.4} height={2.3} doorWidth={0.9} />
      <GlassWall position={[-5.2, 0, -2.15]} width={4.4} height={2.3} doorWidth={0.9} />
      <GlassWall position={[-5.2, 0, 2.15]} width={4.4} height={2.3} doorWidth={0.9} />

      {/* ═══════════════════════════════════════════════════════════
          6. ARCHITECTURAL COLUMNS
          ═══════════════════════════════════════════════════════════ */}
      <ArchColumn position={[-2.95, 0, -2.15]} />
      <ArchColumn position={[2.95, 0, -2.15]} />
      <ArchColumn position={[-2.95, 0, 2.15]} />
      <ArchColumn position={[2.95, 0, 2.15]} />

      {/* ═══════════════════════════════════════════════════════════
          7. CORRIDOR / WORKPLACE ZONING
          ═══════════════════════════════════════════════════════════ */}

      {/* NW Corner: Executive Espresso Bar & Refreshment Zone */}
      <group position={[-6.2, 0, -4.5]}>
        <mesh position={[0, 0.45, 0]} castShadow>
          <boxGeometry args={[1.4, 0.9, 0.5]} />
          <meshStandardMaterial color="#334155" roughness={0.3} metalness={0.6} />
        </mesh>
        <mesh position={[0, 0.91, 0]}>
          <boxGeometry args={[1.44, 0.03, 0.54]} />
          <meshStandardMaterial color="#f8fafc" roughness={0.2} />
        </mesh>
        <mesh position={[-0.4, 1.05, 0]} castShadow>
          <boxGeometry args={[0.35, 0.26, 0.28]} />
          <meshStandardMaterial color="#0f172a" roughness={0.3} metalness={0.8} />
        </mesh>
        <mesh position={[0.1, 0.95, 0.1]} castShadow>
          <cylinderGeometry args={[0.035, 0.03, 0.07, 10]} />
          <meshStandardMaterial color="#f8fafc" roughness={0.3} />
        </mesh>
        <group position={[0, 0, 0.65]}>
          <mesh position={[-0.4, 0.3, 0]} castShadow>
            <cylinderGeometry args={[0.18, 0.18, 0.04, 12]} />
            <meshStandardMaterial color="#3d2817" roughness={0.5} />
          </mesh>
          <mesh position={[0.4, 0.3, 0]} castShadow>
            <cylinderGeometry args={[0.18, 0.18, 0.04, 12]} />
            <meshStandardMaterial color="#3d2817" roughness={0.5} />
          </mesh>
        </group>
      </group>

      {/* NE Corner: Tech Collaboration Lounge */}
      <group position={[6.2, 0, -4.5]}>
        <mesh position={[0, 0.22, 0]} castShadow>
          <boxGeometry args={[1.5, 0.44, 0.65]} />
          <meshStandardMaterial color="#475569" roughness={0.6} />
        </mesh>
        <mesh position={[0, 0.44, -0.28]} castShadow>
          <boxGeometry args={[1.5, 0.44, 0.12]} />
          <meshStandardMaterial color="#334155" roughness={0.6} />
        </mesh>
        <mesh position={[0, 0.2, 0.6]} castShadow>
          <boxGeometry args={[0.9, 0.06, 0.45]} />
          <meshStandardMaterial color="#3d2817" roughness={0.4} />
        </mesh>
        <Plant3D position={[0.85, 0, 0.6]} scale={0.75} />
      </group>

      {/* SE Corner: Water Cooler & Document Recycling Hub */}
      <group position={[6.2, 0, 4.2]}>
        <Plant3D position={[0, 0, 0]} scale={0.85} />
        <group position={[0.6, 0, -0.6]}>
          <mesh position={[0, 0.55, 0]} castShadow>
            <boxGeometry args={[0.4, 1.1, 0.35]} />
            <meshStandardMaterial color="#e2e8f0" roughness={0.3} />
          </mesh>
          <mesh position={[0, 1.15, 0]} castShadow>
            <cylinderGeometry args={[0.16, 0.16, 0.3, 12]} />
            <meshStandardMaterial color="#38bdf8" transparent opacity={0.65} />
          </mesh>
        </group>
        <mesh position={[-0.6, 0.2, 0]} castShadow>
          <boxGeometry args={[0.3, 0.4, 0.3]} />
          <meshStandardMaterial color="#16a34a" roughness={0.4} />
        </mesh>
      </group>

      {/* SW Corner: Ops Document Station */}
      <group position={[-6.2, 0, 4.2]}>
        <Plant3D position={[0, 0, 0]} scale={0.9} />
        <mesh position={[-0.5, 0.35, 0.6]} castShadow>
          <boxGeometry args={[1.2, 0.7, 0.5]} />
          <meshStandardMaterial color="#475569" roughness={0.35} metalness={0.6} />
        </mesh>
      </group>

      <Plant3D position={[0, 0, -2.15]} scale={0.5} />

      {/* ═══════════════════════════════════════════════════════════
          8. CENTRAL AI ORCHESTRATOR CORE VISUALIZER
          ═══════════════════════════════════════════════════════════ */}
      <OrchestratorCore3D
        activeAgent={activeAgent}
        orchestrationStatus={orchestrationStatus}
      />

      {/* ═══════════════════════════════════════════════════════════
          9. INTER-AGENT COMMUNICATION PULSE BEAMS
          ═══════════════════════════════════════════════════════════ */}
      <CommunicationSignal3D signals={activeSignals} />

      {/* ═══════════════════════════════════════════════════════════
          10. DEPARTMENT ROOM OVERLAY STATUS BADGES (Non-Overlapping Anchor Strategy)
          ═══════════════════════════════════════════════════════════ */}
      <DepartmentStatusBadge3D
        department="CEO"
        position={[0, 3.4, -5.6]}
        state={ceoTask.state}
        taskTitle={ceoTask.taskTitle}
        progress={ceoTask.progress}
        blockerReason={ceoTask.blockerReason}
        isActive={activeAgent === "CEO"}
        onSelect={() => onSelectDepartment("CEO")}
      />

      <DepartmentStatusBadge3D
        department="DEVELOPMENT"
        position={[5.2, 3.4, -1.8]}
        state={devTask.state}
        taskTitle={devTask.taskTitle}
        progress={devTask.progress}
        blockerReason={devTask.blockerReason}
        isActive={activeAgent === "DEVELOPMENT"}
        onSelect={() => onSelectDepartment("DEVELOPMENT")}
      />

      <DepartmentStatusBadge3D
        department="MARKETING"
        position={[-5.2, 3.4, -1.8]}
        state={mktTask.state}
        taskTitle={mktTask.taskTitle}
        progress={mktTask.progress}
        blockerReason={mktTask.blockerReason}
        isActive={activeAgent === "MARKETING"}
        onSelect={() => onSelectDepartment("MARKETING")}
      />

      <DepartmentStatusBadge3D
        department="FINANCE"
        position={[0, 3.4, 5.6]}
        state={finTask.state}
        taskTitle={finTask.taskTitle}
        progress={finTask.progress}
        blockerReason={finTask.blockerReason}
        isActive={activeAgent === "FINANCE"}
        onSelect={() => onSelectDepartment("FINANCE")}
      />

      <DepartmentStatusBadge3D
        department="BOARDROOM"
        position={[0, 3.4, 0.8]}
        state={boardroomTask.state}
        taskTitle={boardroomTask.taskTitle}
        progress={boardroomTask.progress}
        blockerReason={boardroomTask.blockerReason}
        isActive={activeAgent === "BOARDROOM"}
        onSelect={onOpenBoardroom}
      />

      {/* ═══════════════════════════════════════════════════════════
          11. DEPARTMENT ROOM COMPONENTS (With Active Glow Boost)
          ═══════════════════════════════════════════════════════════ */}
      <CEOOffice3D
        onSelectDepartment={() => onSelectDepartment("CEO")}
        active={activeAgent === "CEO"}
      />
      <DevelopmentOffice3D
        onSelectDepartment={() => onSelectDepartment("DEVELOPMENT")}
        active={activeAgent === "DEVELOPMENT"}
      />
      <MarketingOffice3D
        onSelectDepartment={() => onSelectDepartment("MARKETING")}
        active={activeAgent === "MARKETING"}
      />
      <FinanceOffice3D
        onSelectDepartment={() => onSelectDepartment("FINANCE")}
        active={activeAgent === "FINANCE"}
      />
      <Boardroom3D
        onOpenBoardroom={onOpenBoardroom}
        activeSpeaker={activeSpeaker}
        currentRound={currentRound}
        hasDebate={inDebate}
      />

      {/* ═══════════════════════════════════════════════════════════
          12. 3D HUMANOID EMPLOYEES
          ═══════════════════════════════════════════════════════════ */}
      {EMPLOYEES.map((emp) => {
        const isSpeaker =
          activeSpeaker !== null &&
          (activeSpeaker.toLowerCase().includes(emp.department.toLowerCase()) ||
            (emp.department === "CEO" && activeSpeaker.toLowerCase().includes("ceo")));

        return (
          <Employee3D
            key={emp.id}
            info={emp}
            activity={getEmployeeActivity(emp.department)}
            inBoardroom={inDebate}
            isCurrentSpeaker={isSpeaker}
            onSelectDepartment={(d) => onSelectDepartment(d as AgentType)}
          />
        );
      })}
    </group>
  );
}

export default memo(OfficeSceneComponent);
