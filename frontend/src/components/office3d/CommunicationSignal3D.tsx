import { useRef, useMemo, memo } from "react";
import { useFrame } from "@react-three/fiber";
import { Html } from "@react-three/drei";
import * as THREE from "three";
import type { AgentType } from "../../types";
import { ROOM_LAYOUTS } from "./officeLayout";

export interface ActiveSignal {
  id: string;
  from: AgentType | "BOARDROOM";
  to: AgentType | "BOARDROOM";
  label: string;
  startTime: number;
}

interface CommunicationSignal3DProps {
  signals: ActiveSignal[];
}

const DEPT_POSITIONS: Record<AgentType | "BOARDROOM", THREE.Vector3> = {
  CEO: new THREE.Vector3(ROOM_LAYOUTS.CEO.center.x, 0.8, ROOM_LAYOUTS.CEO.center.z),
  DEVELOPMENT: new THREE.Vector3(ROOM_LAYOUTS.DEVELOPMENT.center.x, 0.8, ROOM_LAYOUTS.DEVELOPMENT.center.z),
  MARKETING: new THREE.Vector3(ROOM_LAYOUTS.MARKETING.center.x, 0.8, ROOM_LAYOUTS.MARKETING.center.z),
  FINANCE: new THREE.Vector3(ROOM_LAYOUTS.FINANCE.center.x, 0.8, ROOM_LAYOUTS.FINANCE.center.z),
  BOARDROOM: new THREE.Vector3(ROOM_LAYOUTS.BOARDROOM.center.x, 0.8, ROOM_LAYOUTS.BOARDROOM.center.z),
};

function SignalItem({ signal }: { signal: ActiveSignal }) {
  const particleRef = useRef<THREE.Mesh>(null);
  const fromPos = DEPT_POSITIONS[signal.from] || DEPT_POSITIONS.CEO;
  const toPos = DEPT_POSITIONS[signal.to] || DEPT_POSITIONS.BOARDROOM;

  const getSignalColorHex = (dept: string) => {
    if (dept === "CEO") return 0xf59e0b;
    if (dept === "DEVELOPMENT") return 0x06b6d4;
    if (dept === "MARKETING") return 0xec4899;
    if (dept === "FINANCE") return 0x10b981;
    return 0xa78bfa;
  };

  const getSignalColorString = (dept: string) => {
    if (dept === "CEO") return "#f59e0b";
    if (dept === "DEVELOPMENT") return "#06b6d4";
    if (dept === "MARKETING") return "#ec4899";
    if (dept === "FINANCE") return "#10b981";
    return "#a78bfa";
  };

  const colorHex = getSignalColorHex(signal.from);
  const colorStr = getSignalColorString(signal.from);

  // Arc path geometry curve & Line object
  const { curve, lineObj, midPoint } = useMemo(() => {
    const mid = new THREE.Vector3()
      .addVectors(fromPos, toPos)
      .multiplyScalar(0.5);
    mid.y += 1.4; // Arch upward in 3D space

    const c = new THREE.QuadraticBezierCurve3(fromPos, mid, toPos);
    const points = c.getPoints(24);
    const geo = new THREE.BufferGeometry().setFromPoints(points);
    const mat = new THREE.LineBasicMaterial({
      color: colorHex,
      transparent: true,
      opacity: 0.65,
    });
    const lObj = new THREE.Line(geo, mat);
    return { curve: c, lineObj: lObj, midPoint: mid };
  }, [fromPos, toPos, colorHex]);

  useFrame(({ clock }) => {
    const elapsed = clock.getElapsedTime() - signal.startTime;
    const duration = 1.8;
    const progress = Math.min(Math.max(elapsed / duration, 0), 1);

    if (particleRef.current) {
      const pos = curve.getPoint(progress);
      particleRef.current.position.copy(pos);
    }
  });

  return (
    <group>
      {/* Curved Energy Arc */}
      <primitive object={lineObj} />

      {/* Traveling Energy Pulse Node */}
      <mesh ref={particleRef} position={fromPos}>
        <sphereGeometry args={[0.1, 12, 12]} />
        <meshBasicMaterial color={colorStr} />
        <pointLight color={colorStr} intensity={2.0} distance={2.5} />
      </mesh>

      {/* Temporary Floating Signal Label */}
      <Html position={midPoint} center distanceFactor={8} zIndexRange={[10, 0]}>
        <div className="bg-slate-900/90 text-white backdrop-blur-md px-2.5 py-1 rounded-full border border-slate-700/80 text-[10px] font-bold shadow-xl animate-fade-in pointer-events-none whitespace-nowrap select-none will-change-transform">
          💬 {signal.from} → {signal.to}
        </div>
      </Html>
    </group>
  );
}

function CommunicationSignal3DComponent({ signals }: CommunicationSignal3DProps) {
  if (!signals || signals.length === 0) return null;

  return (
    <group>
      {signals.map((sig) => (
        <SignalItem key={sig.id} signal={sig} />
      ))}
    </group>
  );
}

export default memo(CommunicationSignal3DComponent);
