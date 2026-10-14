import { useRef, useMemo, useEffect, memo } from "react";
import * as THREE from "three";
import { useSim } from "../../store/simulationStore";
import type { AgentType, Plans, Startup, Agent, ExecutionState, Debate } from "../../types";

interface Monitor3DProps {
  type: AgentType | "BOARDROOM";
  position?: [number, number, number];
  rotation?: [number, number, number];
  scale?: number;
  activeSpeaker?: string | null;
  currentRound?: number | null;
}

type ScreenState = "IDLE" | "WORKING" | "COMMUNICATING" | "COMPLETED" | "BLOCKED" | "FAILED";

interface ScreenModel {
  status: ScreenState;
  task: string | null;
  progress: number | null;
  blocker: string | null;
  findings: { label: string; value: string }[];
}

const SCREEN: Record<AgentType, { bar: string; accent: string; icon: string; title: string }> = {
  CEO: { bar: "#422006", accent: "#fbbf24", icon: "🎯", title: "CEO · STRATEGY" },
  DEVELOPMENT: { bar: "#082f3a", accent: "#38bdf8", icon: "⚡", title: "DEV · BUILD" },
  MARKETING: { bar: "#3b0a2e", accent: "#f472b6", icon: "📈", title: "MKT · GROWTH" },
  FINANCE: { bar: "#052e24", accent: "#34d399", icon: "💵", title: "FIN · RUNWAY" },
};

const STATUS_STYLE: Record<ScreenState, { label: string; color: string }> = {
  IDLE: { label: "IDLE", color: "#64748b" },
  WORKING: { label: "● WORKING", color: "#38bdf8" },
  COMMUNICATING: { label: "● COMMUNICATING", color: "#a78bfa" },
  COMPLETED: { label: "✔ COMPLETED", color: "#34d399" },
  BLOCKED: { label: "⚠ BLOCKED", color: "#f59e0b" },
  FAILED: { label: "✕ FAILED", color: "#f87171" },
};

function clean(s: string | null | undefined): string | null {
  if (!s) return null;
  const t = s.trim();
  return t.length ? t : null;
}

function money(n: number | null | undefined): string {
  if (n == null || !isFinite(n)) return "—";
  if (Math.abs(n) >= 1000) return "$" + Math.round(n / 1000) + "k";
  return "$" + Math.round(n);
}

interface StoreSlices {
  agents: Agent[];
  executionState: ExecutionState | null;
  plans: Plans | null;
  startup: Startup | null;
  debates: Debate[];
}

function buildModel(type: AgentType, s: StoreSlices): ScreenModel {
  const { agents, executionState, plans, startup, debates } = s;
  const agent = agents.find((a) => a.type === type);
  const agState = agent?.state ?? "IDLE";
  const deptTask = executionState?.tasks?.find(
    (t) => t.department === type && (t.status === "IN_PROGRESS" || t.status === "BLOCKED")
  );
  const deptProgress = executionState?.departmentProgress?.[type] ?? (deptTask ? deptTask.progress : null);
  const failed =
    (type === "CEO" && !!startup?.ceoAnalysisFailed) ||
    (type === "DEVELOPMENT" && !!plans?.technical?.analysisFailed) ||
    (type === "MARKETING" && !!plans?.marketing?.analysisFailed) ||
    (type === "FINANCE" && !!plans?.budget?.analysisFailed);
  let status: ScreenState;
  if (failed) status = "FAILED";
  else if (deptTask?.status === "BLOCKED" || agState === "BLOCKED") status = "BLOCKED";
  else if (agState === "DISCUSSING" || agState === "MEETING") status = "COMMUNICATING";
  else if (deptTask || agState === "WORKING" || agState === "THINKING" || agState === "DECIDING") status = "WORKING";
  else if (agState === "COMPLETED") status = "COMPLETED";
  else status = "IDLE";
  const task = clean(deptTask?.title) ?? clean(agent?.currentActivity);
  const progress = deptTask ? deptTask.progress : deptProgress;
  const findings: { label: string; value: string }[] = [];
  const push = (label: string, value: string | null) => {
    if (value) findings.push({ label, value });
  };
  if (type === "CEO") {
    const decision = debates[debates.length - 1]?.decision?.decision;
    push("Objective", clean(startup?.strategicObjectives?.[0]) ?? clean(startup?.mvpDirection));
    push("Decision", clean(decision));
    push("Summary", clean(startup?.executiveSummary));
  } else if (type === "DEVELOPMENT") {
    push("Stack", clean(plans?.technical?.techStack));
    push("Architecture", clean(plans?.technical?.architecture));
    push("Timeline", clean(plans?.technical?.timeline));
  } else if (type === "MARKETING") {
    push("Positioning", clean(plans?.marketing?.positioning));
    push("Channels", clean(plans?.marketing?.channels));
    push("Insight", clean(plans?.marketing?.competitorAnalysis));
  } else if (type === "FINANCE") {
    const runway = executionState?.runwayMonths ?? plans?.budget?.runwayMonths;
    if (runway != null && isFinite(runway)) push("Runway", runway.toFixed(1) + " mo");
    if (executionState?.budgetRemaining != null) push("Budget", money(executionState.budgetRemaining) + " left");
    if (plans?.budget?.monthlyBurn) push("Burn", money(plans.budget.monthlyBurn) + "/mo");
    push("Break-even", clean(plans?.budget?.breakEvenAssumption));
  }
  return { status, task, progress: progress ?? null, blocker: clean(deptTask?.blockerReason), findings };
}

function wrapLines(ctx: CanvasRenderingContext2D, text: string, maxWidth: number, maxLines: number): string[] {
  const words = text.split(/\s+/);
  const lines: string[] = [];
  let line = "";
  for (const w of words) {
    const trial = line ? line + " " + w : w;
    if (ctx.measureText(trial).width > maxWidth && line) {
      lines.push(line);
      line = w;
      if (lines.length === maxLines) break;
    } else {
      line = trial;
    }
  }
  if (lines.length < maxLines && line) lines.push(line);
  if (lines.length === maxLines) {
    let last = lines[maxLines - 1];
    while (last.length > 1 && ctx.measureText(last + "…").width > maxWidth) last = last.slice(0, -1);
    lines[maxLines - 1] = last + "…";
  }
  return lines;
}

function drawDepartmentScreen(ctx: CanvasRenderingContext2D, type: AgentType, model: ScreenModel) {
  const W = ctx.canvas.width;
  const H = ctx.canvas.height;
  const S = W / 512;
  const theme = SCREEN[type];
  const st = STATUS_STYLE[model.status];
  const pad = 22 * S;
  const innerW = W - pad * 2;

  ctx.fillStyle = "#0f1117";
  ctx.fillRect(0, 0, W, H);

  // Header — charcoal/navy with department accent
  ctx.fillStyle = theme.bar;
  ctx.fillRect(0, 0, W, 46 * S);
  ctx.fillStyle = theme.accent;
  ctx.font = `bold ${19 * S}px sans-serif`;
  ctx.textBaseline = "alphabetic";
  ctx.fillText(`${theme.icon}  ${theme.title}`, pad, 30 * S);

  // Prominent status badge — right-aligned, larger
  ctx.font = `bold ${14 * S}px monospace`;
  ctx.fillStyle = st.color;
  const badgeW = ctx.measureText(st.label).width;
  ctx.fillText(st.label, W - badgeW - pad, 30 * S);

  // Clip body to prevent spill outside bezel
  ctx.save();
  ctx.beginPath();
  ctx.rect(pad, 46 * S, innerW, H - 46 * S - pad);
  ctx.clip();

  let y = 74 * S;

  // Section label
  ctx.font = `${11 * S}px sans-serif`;
  ctx.fillStyle = "#64748b";
  ctx.fillText("CURRENT TASK", pad, y);
  y += 22 * S;

  // Task — large, high-contrast, primary hierarchy
  ctx.font = `bold ${20 * S}px sans-serif`;
  ctx.fillStyle = "#f1f5f9";
  const taskText = model.task ?? (model.status === "IDLE" ? "Standby — awaiting activity" : "—");
  const taskLines = wrapLines(ctx, taskText, innerW, 3);
  for (const ln of taskLines) {
    if (y > H - 24 * S) break;
    ctx.fillText(ln, pad, y);
    y += 24 * S;
  }

  // Progress or blocker — only when data exists
  if (model.blocker) {
    y += 6 * S;
    ctx.font = `${13 * S}px sans-serif`;
    ctx.fillStyle = "#fbbf24";
    const bLines = wrapLines(ctx, "⚠ " + model.blocker, innerW, 2);
    for (const ln of bLines) {
      if (y > H - 18 * S) break;
      ctx.fillText(ln, pad, y);
      y += 18 * S;
    }
  } else if (model.progress != null) {
    y += 8 * S;
    const barH = 14 * S;
    const barY = y;
    ctx.fillStyle = "#1e293b";
    ctx.fillRect(pad, barY, innerW, barH);
    ctx.fillStyle = theme.accent;
    ctx.fillRect(pad, barY, innerW * Math.max(0, Math.min(100, model.progress)) / 100, barH);
    // Centered percent label inside bar for readability
    ctx.fillStyle = model.progress > 50 ? "#0f1117" : "#e2e8f0";
    ctx.font = `bold ${11 * S}px monospace`;
    ctx.textAlign = "center";
    ctx.fillText(`${Math.round(model.progress)}%`, W / 2, barY + 10.5 * S);
    ctx.textAlign = "left";
    y = barY + barH + 14 * S;
  } else {
    y += 8 * S;
  }

  // Divider
  ctx.strokeStyle = "#1e293b";
  ctx.lineWidth = 1 * S;
  ctx.beginPath();
  ctx.moveTo(pad, y);
  ctx.lineTo(W - pad, y);
  ctx.stroke();
  y += 20 * S;

  // Findings — at most 2 concise entries, clipped
  const findings = model.findings.slice(0, 2);
  if (findings.length === 0) {
    ctx.font = `${13 * S}px sans-serif`;
    ctx.fillStyle = "#475569";
    ctx.fillText("No results yet.", pad, y);
  } else {
    for (const f of findings) {
      if (y > H - 16 * S) break;
      ctx.font = `bold ${11 * S}px sans-serif`;
      ctx.fillStyle = theme.accent;
      ctx.fillText(f.label.toUpperCase(), pad, y);
      y += 17 * S;
      ctx.font = `${13 * S}px sans-serif`;
      ctx.fillStyle = "#cbd5e1";
      const vLines = wrapLines(ctx, f.value, innerW, 2);
      for (const ln of vLines) {
        if (y > H - 12 * S) break;
        ctx.fillText(ln, pad, y);
        y += 17 * S;
      }
      y += 8 * S;
    }
  }

  ctx.restore();
}

function drawBoardroomScreen(ctx: CanvasRenderingContext2D, speaker: string | null, round: number | null) {
  const W = ctx.canvas.width;
  const H = ctx.canvas.height;
  const S = W / 512;
  const pad = 22 * S;
  ctx.fillStyle = "#0f1117";
  ctx.fillRect(0, 0, W, H);
  ctx.fillStyle = "#2a1848";
  ctx.fillRect(0, 0, W, 48 * S);
  ctx.fillStyle = "#ffffff";
  ctx.font = `bold ${19 * S}px sans-serif`;
  ctx.textBaseline = "alphabetic";
  ctx.fillText("⚖️  BOARDROOM DEBATE", pad, 31 * S);
  ctx.fillStyle = "#a78bfa";
  ctx.font = `bold ${15 * S}px sans-serif`;
  ctx.fillText(`STATUS: ${round ? `ROUND ${round}` : "STRATEGIC DEBATE"}`, pad, 84 * S);
  ctx.fillStyle = "#c4b5fd";
  ctx.font = `${14 * S}px sans-serif`;
  const speakerLines = wrapLines(ctx, `SPEAKER: ${speaker || "—"}`, W - pad * 2, 2);
  let sy = 112 * S;
  for (const ln of speakerLines) {
    ctx.fillText(ln, pad, sy);
    sy += 18 * S;
  }
}

function Monitor3DComponent({
  type,
  position = [0, 0, 0],
  rotation = [0, 0, 0],
  scale = 1,
  activeSpeaker,
  currentRound,
}: Monitor3DProps) {
  const textureRef = useRef<THREE.CanvasTexture | null>(null);

  const agents = useSim((s) => s.agents);
  const executionState = useSim((s) => s.executionState);
  const plans = useSim((s) => s.plans);
  const startup = useSim((s) => s.startup);
  const debates = useSim((s) => s.debates);

  const canvas = useMemo(() => {
    const c = document.createElement("canvas");
    c.width = 1024;
    c.height = 640;
    return c;
  }, []);

  const texture = useMemo(() => {
    const tex = new THREE.CanvasTexture(canvas);
    tex.minFilter = THREE.LinearFilter;
    tex.magFilter = THREE.LinearFilter;
    tex.colorSpace = THREE.SRGBColorSpace;
    tex.needsUpdate = true;
    textureRef.current = tex;
    return tex;
  }, [canvas]);

  useEffect(() => {
    return () => {
      textureRef.current?.dispose();
    };
  }, []);

  const model = useMemo<ScreenModel | null>(() => {
    if (type === "BOARDROOM") return null;
    return buildModel(type, { agents, executionState, plans, startup, debates });
  }, [type, agents, executionState, plans, startup, debates]);

  useEffect(() => {
    const ctx = canvas.getContext("2d");
    if (!ctx) return;
    if (type === "BOARDROOM") {
      drawBoardroomScreen(ctx, activeSpeaker ?? null, currentRound ?? null);
    } else if (model) {
      drawDepartmentScreen(ctx, type, model);
    }
    if (textureRef.current) textureRef.current.needsUpdate = true;
  }, [canvas, type, model, activeSpeaker, currentRound]);

  const glow =
    type === "DEVELOPMENT"
      ? "#06b6d4"
      : type === "MARKETING"
        ? "#ec4899"
        : type === "FINANCE"
          ? "#10b981"
          : type === "BOARDROOM"
            ? "#a78bfa"
            : "#f59e0b";

  return (
    <group position={position} rotation={rotation} scale={scale}>
      <mesh position={[0, -0.22, 0]} castShadow>
        <boxGeometry args={[0.26, 0.02, 0.2]} />
        <meshStandardMaterial color="#1e293b" roughness={0.2} metalness={0.8} />
      </mesh>
      <mesh position={[0, -0.1, -0.05]} rotation={[0.08, 0, 0]} castShadow>
        <boxGeometry args={[0.045, 0.26, 0.045]} />
        <meshStandardMaterial color="#475569" roughness={0.2} metalness={0.9} />
      </mesh>
      <mesh position={[0, 0.14, 0]} castShadow>
        <boxGeometry args={[0.76, 0.46, 0.035]} />
        <meshStandardMaterial color="#0f172a" roughness={0.2} metalness={0.85} />
      </mesh>
      <mesh position={[0, 0.14, 0.02]}>
        <planeGeometry args={[0.72, 0.45]} />
        <meshBasicMaterial map={texture} toneMapped={false} />
      </mesh>
      <pointLight position={[0, 0.14, 0.25]} intensity={0.55} distance={1.6} color={glow} />
    </group>
  );
}

export default memo(Monitor3DComponent);
