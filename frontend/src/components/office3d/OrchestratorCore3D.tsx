import { useRef, useMemo, memo } from "react";
import { useFrame } from "@react-three/fiber";
import { Html } from "@react-three/drei";
import * as THREE from "three";
import type { AgentType } from "../../types";
import { ROOM_LAYOUTS } from "./officeLayout";

interface OrchestratorCore3DProps {
  activeAgent: AgentType | "BOARDROOM" | null;
  orchestrationStatus: string;
}

// Destination coordinates for each department
const DEPT_COORDS: Record<AgentType | "BOARDROOM", THREE.Vector3> = {
  CEO: new THREE.Vector3(ROOM_LAYOUTS.CEO.center.x, 0.05, ROOM_LAYOUTS.CEO.center.z),
  DEVELOPMENT: new THREE.Vector3(ROOM_LAYOUTS.DEVELOPMENT.center.x, 0.05, ROOM_LAYOUTS.DEVELOPMENT.center.z),
  MARKETING: new THREE.Vector3(ROOM_LAYOUTS.MARKETING.center.x, 0.05, ROOM_LAYOUTS.MARKETING.center.z),
  FINANCE: new THREE.Vector3(ROOM_LAYOUTS.FINANCE.center.x, 0.05, ROOM_LAYOUTS.FINANCE.center.z),
  BOARDROOM: new THREE.Vector3(ROOM_LAYOUTS.BOARDROOM.center.x, 0.05, ROOM_LAYOUTS.BOARDROOM.center.z),
};

const CENTER_POS = new THREE.Vector3(0, 0.05, 0);

function OrchestratorCore3DComponent({
  activeAgent,
  orchestrationStatus,
}: OrchestratorCore3DProps) {
  const coreRef = useRef<THREE.Group>(null);
  const ringRef = useRef<THREE.Mesh>(null);
  const pulsePulseRef = useRef<THREE.Mesh>(null);

  // Pre-calculate line objects from central core to each department
  const conduitObjects = useMemo(() => {
    const keys: (AgentType | "BOARDROOM")[] = ["CEO", "DEVELOPMENT", "MARKETING", "FINANCE"];
    return keys.map((key) => {
      const dest = DEPT_COORDS[key];
      const points = [CENTER_POS.clone(), dest.clone()];
      const geometry = new THREE.BufferGeometry().setFromPoints(points);
      const material = new THREE.LineBasicMaterial({
        color: "#334155",
        transparent: true,
        opacity: 0.4,
      });
      const lineObj = new THREE.Line(geometry, material);
      return { key, lineObj, material, dest };
    });
  }, []);

  useFrame(({ clock }) => {
    const t = clock.getElapsedTime();

    if (ringRef.current) {
      ringRef.current.rotation.z = t * 0.8;
    }
    if (pulsePulseRef.current) {
      const scale = 1 + Math.sin(t * 3) * 0.08;
      pulsePulseRef.current.scale.set(scale, scale, scale);
    }

    // Update conduit line materials dynamically
    conduitObjects.forEach(({ key, material }) => {
      const isActive = activeAgent === key;
      const colorHex = isActive
        ? key === "CEO"
          ? 0xf59e0b
          : key === "DEVELOPMENT"
          ? 0x06b6d4
          : key === "MARKETING"
          ? 0xec4899
          : 0x10b981
        : 0x334155;
      material.color.setHex(colorHex);
      material.opacity = isActive ? 0.9 : 0.4;
    });
  });

  const getActiveColor = () => {
    if (!activeAgent) return "#38bdf8";
    if (activeAgent === "CEO") return "#f59e0b";
    if (activeAgent === "DEVELOPMENT") return "#06b6d4";
    if (activeAgent === "MARKETING") return "#ec4899";
    if (activeAgent === "FINANCE") return "#10b981";
    if (activeAgent === "BOARDROOM") return "#a78bfa";
    return "#38bdf8";
  };

  const currentColor = getActiveColor();

  return (
    <group position={[0, 0.02, 0]}>
      {/* ─── Ground Conduit Lines to Departments ─── */}
      {conduitObjects.map(({ key, lineObj }) => {
        const isActive = activeAgent === key;
        const color = key === "CEO"
          ? "#f59e0b"
          : key === "DEVELOPMENT"
          ? "#06b6d4"
          : key === "MARKETING"
          ? "#ec4899"
          : "#10b981";

        return (
          <group key={key}>
            <primitive object={lineObj} />
            {/* Glowing active node at department junction */}
            {isActive && (
              <mesh position={DEPT_COORDS[key]}>
                <cylinderGeometry args={[0.22, 0.22, 0.01, 16]} />
                <meshBasicMaterial color={color} transparent opacity={0.6} />
              </mesh>
            )}
          </group>
        );
      })}

      {/* ─── Central Orchestrator 3D Core Node ─── */}
      <group ref={coreRef} position={[0, 0.08, 0]}>
        {/* Outer Metallic Ring */}
        <mesh ref={ringRef} rotation={[-Math.PI / 2, 0, 0]}>
          <ringGeometry args={[0.32, 0.42, 32]} />
          <meshStandardMaterial color="#1e293b" metalness={0.9} roughness={0.2} />
        </mesh>

        {/* Pulsing Core Sphere */}
        <mesh ref={pulsePulseRef}>
          <sphereGeometry args={[0.22, 16, 16]} />
          <meshStandardMaterial
            color={currentColor}
            emissive={currentColor}
            emissiveIntensity={activeAgent ? 0.8 : 0.4}
            roughness={0.2}
          />
        </mesh>

        {/* Soft Light Emission */}
        <pointLight
          position={[0, 0.3, 0]}
          color={currentColor}
          intensity={activeAgent ? 2.2 : 1.2}
          distance={4.5}
        />

        {/* Floating 3D Orchestrator HUD Label (Elevated Crown Anchor) */}
        <Html position={[0, 3.1, 0]} center distanceFactor={9.5} zIndexRange={[15, 0]}>
          <div className="bg-slate-950/95 text-cyan-300 px-3 py-1 rounded-full border border-cyan-500/60 text-[10.5px] font-mono font-bold shadow-2xl flex items-center gap-1.5 pointer-events-none select-none whitespace-nowrap will-change-transform">
            <span className="w-2 h-2 rounded-full bg-cyan-400 animate-ping" />
            <span>⚡ ORCHESTRATOR: {orchestrationStatus.toUpperCase()}</span>
          </div>
        </Html>
      </group>
    </group>
  );
}

export default memo(OrchestratorCore3DComponent);
