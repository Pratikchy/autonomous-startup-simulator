import { useRef, useState, useCallback, useEffect, Suspense, memo } from "react";
import { Canvas, useFrame, useThree } from "@react-three/fiber";
import { OrbitControls, PerspectiveCamera, Html } from "@react-three/drei";
import type { OrbitControls as OrbitControlsImpl } from "three-stdlib";
import * as THREE from "three";
import type { AgentType } from "../../types";
import { useSim } from "../../store/simulationStore";
import OfficeScene from "./OfficeScene";

interface StartupOffice3DProps {
  onSelectDepartment: (dept: AgentType) => void;
  onOpenBoardroom: () => void;
}

/* ─── Pre-allocated static Vector3 targets for zero-GC camera lerping ─── */
const ROOM_TARGET_VECS: Record<
  AgentType | "BOARDROOM" | "RESET",
  { target: THREE.Vector3; pos: THREE.Vector3 }
> = {
  RESET: { target: new THREE.Vector3(0, 1.25, 0), pos: new THREE.Vector3(10.8, 8.2, 11.8) },
  CEO: { target: new THREE.Vector3(0, 1.1, -4.2), pos: new THREE.Vector3(3.2, 5.2, -0.6) },
  DEVELOPMENT: { target: new THREE.Vector3(5.2, 1.1, 0), pos: new THREE.Vector3(9.2, 5.2, 4.2) },
  MARKETING: { target: new THREE.Vector3(-5.2, 1.1, 0), pos: new THREE.Vector3(-9.2, 5.2, 4.2) },
  FINANCE: { target: new THREE.Vector3(0, 1.1, 4.2), pos: new THREE.Vector3(3.2, 5.2, 8.4) },
  BOARDROOM: { target: new THREE.Vector3(0, 1.1, 0), pos: new THREE.Vector3(4.2, 5.5, 5.2) },
};

/* ─── Smooth camera lerp rig (Stops lerping on completion to allow 100% smooth dragging) ─── */
function CameraRig({
  focusedRoom,
  onComplete,
}: {
  focusedRoom: AgentType | "BOARDROOM" | "RESET" | null;
  onComplete: () => void;
}) {
  const transitionTime = useRef(0);

  useFrame(({ camera }, delta) => {
    if (!focusedRoom) return;
    const config = ROOM_TARGET_VECS[focusedRoom];
    if (!config) return;

    transitionTime.current += delta;
    camera.position.lerp(config.pos, Math.min(delta * 4.5, 0.2));

    if (camera.position.distanceTo(config.pos) < 0.05 || transitionTime.current > 0.8) {
      camera.position.copy(config.pos);
      transitionTime.current = 0;
      onComplete();
    }
  });

  return null;
}

/* ─── Shadow Map Frame Throttler (Eliminates redundant 60fps shadow depth recalculation during camera dragging) ─── */
function ShadowMapOptimizer() {
  const { gl } = useThree();
  const frameCount = useRef(0);

  useEffect(() => {
    gl.shadowMap.autoUpdate = false;
    gl.shadowMap.needsUpdate = true;
  }, [gl]);

  useFrame(() => {
    frameCount.current += 1;
    // Throttled refresh maintains crisp shadows while cutting 40+ shadow draw calls/sec during camera rotations
    if (frameCount.current % 3 === 0) {
      gl.shadowMap.needsUpdate = true;
    }
  });

  return null;
}

/* ─── Development Performance Stats Overlay Component ─── */
function PerfStats({ visible }: { visible: boolean }) {
  const { gl } = useThree();
  const [stats, setStats] = useState({ fps: 60, frameTime: 16.6, calls: 0, tris: 0 });
  const frameCount = useRef(0);
  const accumTime = useRef(0);

  useFrame((_, delta) => {
    if (!visible) return;
    frameCount.current += 1;
    accumTime.current += delta;

    if (accumTime.current >= 0.5) {
      const fps = Math.round(frameCount.current / accumTime.current);
      const frameTime = Number((accumTime.current / frameCount.current * 1000).toFixed(1));
      const calls = gl.info.render.calls;
      const tris = gl.info.render.triangles;
      setStats({ fps, frameTime, calls, tris });
      frameCount.current = 0;
      accumTime.current = 0;
    }
  });

  if (!visible) return null;

  return (
    <Html position={[-8, 0, 6.2]} style={{ pointerEvents: "none" }}>
      <div className="bg-slate-900/90 text-emerald-400 font-mono text-[11px] px-3 py-2 rounded-lg border border-slate-700/80 shadow-2xl backdrop-blur-md select-none whitespace-nowrap">
        <div className="font-bold text-white mb-1 flex items-center gap-1.5">
          <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
          ⚡ 3D PERF PROFILE
        </div>
        <div>
          FPS:{" "}
          <span
            className={
              stats.fps >= 55
                ? "text-emerald-400 font-bold"
                : stats.fps >= 30
                ? "text-amber-400 font-bold"
                : "text-rose-400 font-bold"
            }
          >
            {stats.fps}
          </span>
        </div>
        <div>Frame Time: <span className="text-white">{stats.frameTime}ms</span></div>
        <div>Draw Calls: <span className="text-white">{stats.calls}</span></div>
        <div>Triangles: <span className="text-white">{stats.tris.toLocaleString()}</span></div>
      </div>
    </Html>
  );
}

function StartupOffice3DComponent({
  onSelectDepartment,
  onOpenBoardroom,
}: StartupOffice3DProps) {
  const controlsRef = useRef<OrbitControlsImpl | null>(null);
  const [focusedRoom, setFocusedRoom] = useState<AgentType | "BOARDROOM" | "RESET" | null>(null);
  const [showPerf, setShowPerf] = useState(false);

  const debates = useSim((s) => s.debates);
  const hasDebate = debates.length > 0;

  const focusRoom = useCallback((room: AgentType | "BOARDROOM" | "RESET") => {
    setFocusedRoom(room);
    if (controlsRef.current) {
      const target = ROOM_TARGET_VECS[room].target;
      controlsRef.current.target.copy(target);
      controlsRef.current.update();
    }
  }, []);

  const handleResetCamera = useCallback(() => {
    focusRoom("RESET");
  }, [focusRoom]);

  const handleZoom = useCallback((delta: number) => {
    if (controlsRef.current) {
      const camera = controlsRef.current.object;
      const zoomFactor = delta > 0 ? 0.85 : 1.15;
      camera.position.multiplyScalar(zoomFactor);
      controlsRef.current.update();
    }
  }, []);

  const handleRigComplete = useCallback(() => {
    setFocusedRoom(null);
  }, []);

  const handleSelectDepartment = useCallback(
    (dept: AgentType) => {
      focusRoom(dept);
      onSelectDepartment(dept);
    },
    [focusRoom, onSelectDepartment]
  );

  const handleOpenBoardroomSelect = useCallback(() => {
    focusRoom("BOARDROOM");
    onOpenBoardroom();
  }, [focusRoom, onOpenBoardroom]);

  const [perfMode, setPerfMode] = useState<"smooth" | "quality">("smooth");

  return (
    <div className="w-full h-full flex flex-col min-h-0 overflow-hidden select-none bg-slate-950">
      {/* ═══ Dedicated HTML Office Control Header Bar (2 Deterministic Rows) ═══ */}
      <div className="shrink-0 bg-slate-900/95 backdrop-blur-md border-b border-slate-800/80 px-4 py-2 flex flex-col gap-2 z-10 shadow-md">
        {/* ROW 1: Title & Primary Department Navigation */}
        <div className="flex items-center justify-between gap-3 flex-wrap">
          {/* Left: Office Title Indicator */}
          <div className="flex items-center gap-2 shrink-0">
            <div className="w-2.5 h-2.5 rounded-full bg-cyan-400 animate-pulse" />
            <span className="font-bold text-xs tracking-wider text-slate-100 uppercase">
              AI Operations HQ
            </span>
          </div>

          {/* Center/Right: Department Filter Navigation Group */}
          <div className="flex items-center gap-1 bg-slate-950/70 p-1 rounded-full border border-slate-800 text-xs flex-wrap shrink-0">
            <button
              onClick={handleResetCamera}
              className={`flex items-center gap-1 px-2.5 py-1 rounded-full font-bold transition ${
                focusedRoom === "RESET" || !focusedRoom
                  ? "bg-slate-700 text-white shadow"
                  : "text-slate-300 hover:bg-slate-800"
              }`}
              title="HQ Overview Camera Mode"
            >
              🏢 Overview
            </button>
            <button
              onClick={() => {
                focusRoom("CEO");
                onSelectDepartment("CEO");
              }}
              className={`flex items-center gap-1 px-2 py-1 rounded-full text-amber-300 font-medium transition ${
                focusedRoom === "CEO" ? "bg-amber-500/30 font-bold" : "hover:bg-amber-500/20"
              }`}
              title="Focus CEO Suite"
            >
              <span className="w-1.5 h-1.5 rounded-full bg-amber-400" />
              CEO
            </button>
            <button
              onClick={() => {
                focusRoom("DEVELOPMENT");
                onSelectDepartment("DEVELOPMENT");
              }}
              className={`flex items-center gap-1 px-2 py-1 rounded-full text-cyan-300 font-medium transition ${
                focusedRoom === "DEVELOPMENT" ? "bg-cyan-500/30 font-bold" : "hover:bg-cyan-500/20"
              }`}
              title="Focus Dev Bay"
            >
              <span className="w-1.5 h-1.5 rounded-full bg-cyan-400" />
              Dev
            </button>
            <button
              onClick={() => {
                focusRoom("MARKETING");
                onSelectDepartment("MARKETING");
              }}
              className={`flex items-center gap-1 px-2 py-1 rounded-full text-pink-300 font-medium transition ${
                focusedRoom === "MARKETING" ? "bg-pink-500/30 font-bold" : "hover:bg-pink-500/20"
              }`}
              title="Focus Marketing"
            >
              <span className="w-1.5 h-1.5 rounded-full bg-pink-400" />
              Marketing
            </button>
            <button
              onClick={() => {
                focusRoom("FINANCE");
                onSelectDepartment("FINANCE");
              }}
              className={`flex items-center gap-1 px-2.5 py-1 rounded-full text-emerald-300 font-medium transition ${
                focusedRoom === "FINANCE" ? "bg-emerald-500/30 font-bold" : "hover:bg-emerald-500/20"
              }`}
              title="Focus Finance"
            >
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-400" />
              Finance
            </button>
            <button
              onClick={() => {
                focusRoom("BOARDROOM");
                onOpenBoardroom();
              }}
              className={`flex items-center gap-1 px-2.5 py-1 rounded-full text-purple-300 font-medium transition ${
                focusedRoom === "BOARDROOM" ? "bg-purple-500/30 font-bold" : "hover:bg-purple-500/20"
              }`}
              title="Focus Boardroom"
            >
              <span className="w-1.5 h-1.5 rounded-full bg-purple-400" />
              Boardroom
            </button>
          </div>
        </div>

        {/* ROW 2: Action Controls & Camera Utilities */}
        <div className="flex items-center justify-between gap-3 pt-1.5 border-t border-slate-800/60 flex-wrap">
          {/* Left Action Group: View Boardroom Debate */}
          <div className="flex items-center gap-2 shrink-0 min-h-[26px]">
            {hasDebate ? (
              <button
                id="btn-open-boardroom"
                onClick={onOpenBoardroom}
                className="flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-purple-950/90 text-purple-300 border border-purple-500/50 hover:bg-purple-900/90 shadow transition"
              >
                ⚖️ View Boardroom Debate
              </button>
            ) : (
              <span className="text-[11px] font-mono text-slate-400 flex items-center gap-1.5">
                <span className="w-1.5 h-1.5 rounded-full bg-slate-600" />
                SYSTEM ACTIVE
              </span>
            )}
          </div>

          {/* Right Utility Group: Perf Stats, Camera Mode & Zoom Controls */}
          <div className="flex items-center gap-1 bg-slate-950/70 p-1 rounded-lg border border-slate-800 text-xs shrink-0">
            <button
              onClick={() => setPerfMode((m) => (m === "smooth" ? "quality" : "smooth"))}
              className={`px-2 py-0.5 rounded text-[11px] font-mono transition flex items-center gap-1 ${
                perfMode === "smooth"
                  ? "bg-cyan-500/30 text-cyan-300 border border-cyan-500/50 font-bold"
                  : "text-slate-400 hover:bg-slate-800 hover:text-slate-200"
              }`}
              title="Toggle Smooth 60FPS Camera Mode vs High Quality"
            >
              {perfMode === "smooth" ? "🚀 60 FPS" : "🎨 Quality"}
            </button>
            <span className="w-[1px] h-3.5 bg-slate-800" />
            <button
              onClick={() => setShowPerf((v) => !v)}
              className={`px-2 py-0.5 rounded text-[11px] font-mono transition ${
                showPerf
                  ? "bg-emerald-500/30 text-emerald-300 border border-emerald-500/50 font-bold"
                  : "text-slate-400 hover:bg-slate-800 hover:text-slate-200"
              }`}
              title="Toggle 3D Performance Stats HUD"
            >
              ⚡ {showPerf ? "Hide Perf" : "Perf Stats"}
            </button>
            <span className="w-[1px] h-3.5 bg-slate-800" />
            <button
              onClick={() => handleZoom(1)}
              className="w-6 h-6 flex items-center justify-center rounded text-xs font-bold text-slate-300 hover:bg-slate-800 hover:text-white transition"
              title="Zoom In"
            >
              +
            </button>
            <button
              onClick={() => handleZoom(-1)}
              className="w-6 h-6 flex items-center justify-center rounded text-xs font-bold text-slate-300 hover:bg-slate-800 hover:text-white transition"
              title="Zoom Out"
            >
              −
            </button>
          </div>
        </div>
      </div>

      {/* ═══ Hard Bounded 3D Viewport Canvas Container (Layer 0) ═══ */}
      <div className="flex-1 relative min-h-0 overflow-hidden bg-slate-950">
        <Canvas
          shadows={perfMode === "quality"}
          dpr={perfMode === "smooth" ? 1 : [1, 1.25]}
          gl={{
            antialias: true,
            powerPreference: "high-performance",
            alpha: false,
          }}
        >
          {/* Atmospheric depth fog */}
          <fog attach="fog" args={["#161a2b", 28, 55]} />

          <PerspectiveCamera
            makeDefault
            position={[10.8, 8.2, 11.8]}
            fov={35}
            near={0.1}
            far={100}
          />
          <OrbitControls
            ref={controlsRef}
            makeDefault
            enableDamping
            dampingFactor={0.08}
            minDistance={4}
            maxDistance={30}
            minPolarAngle={Math.PI / 6}
            maxPolarAngle={Math.PI / 2.3}
            target={[0, 1.25, 0]}
          />
          <CameraRig focusedRoom={focusedRoom} onComplete={handleRigComplete} />
          <ShadowMapOptimizer />
          <PerfStats visible={showPerf} />
          <Suspense fallback={null}>
            <OfficeScene
              onSelectDepartment={handleSelectDepartment}
              onOpenBoardroom={handleOpenBoardroomSelect}
            />
          </Suspense>
        </Canvas>
      </div>
    </div>
  );
}

export default memo(StartupOffice3DComponent);
