import { memo } from "react";
import { Text } from "@react-three/drei";
import * as THREE from "three";

/* ═══════════════════════════════════════════════════════════════════
   SHARED GEOMETRY & MATERIAL CONSTANTS (Single allocation in memory)
   ═══════════════════════════════════════════════════════════════════ */
const geoDeskTop = new THREE.BoxGeometry(1.5, 0.05, 0.85);
const geoDeskTrim = new THREE.BoxGeometry(1.48, 0.01, 0.83);
const geoDeskMat = new THREE.BoxGeometry(0.9, 0.005, 0.5);
const geoDeskLeg = new THREE.CylinderGeometry(0.025, 0.025, 0.7, 10);
const geoKeyboard = new THREE.BoxGeometry(0.42, 0.012, 0.15);
const geoKeys = new THREE.BoxGeometry(0.38, 0.005, 0.12);
const geoMousepad = new THREE.BoxGeometry(0.22, 0.004, 0.22);
const geoMouse = new THREE.BoxGeometry(0.06, 0.022, 0.09);
const geoCup = new THREE.CylinderGeometry(0.04, 0.035, 0.09, 12);
const geoCoffee = new THREE.CylinderGeometry(0.036, 0.036, 0.005, 12);

const geoChairWheels = new THREE.CylinderGeometry(0.28, 0.28, 0.03, 5);
const geoChairPillar = new THREE.CylinderGeometry(0.035, 0.035, 0.38, 8);
const geoChairSeat = new THREE.BoxGeometry(0.5, 0.08, 0.48);
const geoChairBack = new THREE.BoxGeometry(0.48, 0.62, 0.06);

const geoExecDeskTop = new THREE.BoxGeometry(2.2, 0.08, 1.1);
const geoExecInlay = new THREE.BoxGeometry(1.3, 0.005, 0.65);
const geoExecPanel = new THREE.BoxGeometry(2.1, 0.68, 0.04);
const geoExecPillar = new THREE.BoxGeometry(0.16, 0.68, 1.0);

const geoConfTop = new THREE.BoxGeometry(3.8, 0.06, 1.9);
const geoConfTrim = new THREE.BoxGeometry(3.84, 0.05, 1.94);
const geoConfPedestal = new THREE.CylinderGeometry(0.3, 0.4, 0.7, 16);

const geoPot = new THREE.CylinderGeometry(0.24, 0.18, 0.56, 12);
const geoLeaves1 = new THREE.DodecahedronGeometry(0.38, 1);
const geoLeaves2 = new THREE.DodecahedronGeometry(0.28, 1);
const geoLeaves3 = new THREE.DodecahedronGeometry(0.3, 1);

/* ═══════════════════════════════════════════════════════════════════
   1. Standard Workstation Desk
   ═══════════════════════════════════════════════════════════════════ */
export const Desk3D = memo(function Desk3D({
  position = [0, 0, 0],
  rotation = [0, 0, 0],
  scale = 1,
  color = "#5c4033",
}: {
  position?: [number, number, number];
  rotation?: [number, number, number];
  scale?: number;
  color?: string;
}) {
  return (
    <group position={position} rotation={rotation} scale={scale}>
      {/* Desktop Surface — warm ivory/cream */}
      <mesh geometry={geoDeskTop} position={[0, 0.72, 0]} castShadow receiveShadow>
        <meshStandardMaterial color={color} roughness={0.14} metalness={0.04} />
      </mesh>
      {/* Desk Edge Trim — walnut frame */}
      <mesh geometry={geoDeskTrim} position={[0, 0.74, 0]}>
        <meshStandardMaterial color="#422810" roughness={0.32} />
      </mesh>
      {/* Desk Mat — muted sage, restrained */}
      <mesh geometry={geoDeskMat} position={[0, 0.748, 0.05]}>
        <meshStandardMaterial color="#2f3d33" roughness={0.58} />
      </mesh>

      {/* Metallic Desk Legs */}
      {[-0.68, 0.68].map((x) =>
        [-0.34, 0.34].map((z) => (
          <mesh key={`${x}-${z}`} geometry={geoDeskLeg} position={[x, 0.35, z]} castShadow>
            <meshStandardMaterial color="#78716c" roughness={0.25} metalness={0.85} />
          </mesh>
        ))
      )}

      {/* Keyboard with Visible Key Rows */}
      <group position={[0, 0.755, 0.18]} castShadow>
        <mesh geometry={geoKeyboard}>
          <meshStandardMaterial color="#0f172a" roughness={0.4} />
        </mesh>
        <mesh geometry={geoKeys} position={[0, 0.008, 0]}>
          <meshStandardMaterial color="#334155" roughness={0.6} />
        </mesh>
      </group>

      {/* Mousepad & Mouse */}
      <mesh geometry={geoMousepad} position={[0.32, 0.751, 0.18]}>
        <meshStandardMaterial color="#0f172a" roughness={0.8} />
      </mesh>
      <mesh geometry={geoMouse} position={[0.32, 0.76, 0.18]} castShadow>
        <meshStandardMaterial color="#334155" roughness={0.3} metalness={0.5} />
      </mesh>

      {/* Coffee Cup */}
      <group position={[-0.48, 0.78, 0.15]} castShadow>
        <mesh geometry={geoCup}>
          <meshStandardMaterial color="#f8fafc" roughness={0.2} />
        </mesh>
        <mesh geometry={geoCoffee} position={[0, 0.04, 0]}>
          <meshStandardMaterial color="#451a03" roughness={0.3} />
        </mesh>
      </group>

      {/* Cable Grommet & Monitor Mounting Arm */}
      <mesh position={[0, 0.751, -0.25]}>
        <cylinderGeometry args={[0.035, 0.035, 0.005, 12]} />
        <meshStandardMaterial color="#0f172a" roughness={0.4} metalness={0.8} />
      </mesh>
      <mesh position={[0, 0.88, -0.25]} castShadow>
        <cylinderGeometry args={[0.018, 0.018, 0.26, 8]} />
        <meshStandardMaterial color="#475569" roughness={0.3} metalness={0.9} />
      </mesh>
    </group>
  );
});

/* ═══════════════════════════════════════════════════════════════════
   2. Ergonomic Office Chair
   ═══════════════════════════════════════════════════════════════════ */
export const Chair3D = memo(function Chair3D({
  position = [0, 0, 0],
  rotation = [0, 0, 0],
  scale = 1,
  color = "#3a2d1f",
}: {
  position?: [number, number, number];
  rotation?: [number, number, number];
  scale?: number;
  color?: string;
}) {
  return (
    <group position={position} rotation={rotation} scale={scale}>
      {/* Star Base Wheels */}
      <mesh geometry={geoChairWheels} position={[0, 0.05, 0]} castShadow>
        <meshStandardMaterial color="#0f172a" roughness={0.4} metalness={0.8} />
      </mesh>
      {/* Gas Lift Cylinder */}
      <mesh geometry={geoChairPillar} position={[0, 0.25, 0]} castShadow>
        <meshStandardMaterial color="#64748b" roughness={0.2} metalness={0.9} />
      </mesh>
      {/* Seat Cushion */}
      <mesh geometry={geoChairSeat} position={[0, 0.46, 0]} castShadow receiveShadow>
        <meshStandardMaterial color={color} roughness={0.6} />
      </mesh>
      {/* Lumbar Backrest */}
      <mesh geometry={geoChairBack} position={[0, 0.8, -0.22]} rotation={[-0.1, 0, 0]} castShadow>
        <meshStandardMaterial color={color} roughness={0.6} />
      </mesh>
      {/* Armrests */}
      {[-0.28, 0.28].map((x) => (
        <group key={x} position={[x, 0.62, -0.02]}>
          <mesh castShadow>
            <boxGeometry args={[0.04, 0.24, 0.04]} />
            <meshStandardMaterial color="#1e293b" roughness={0.5} />
          </mesh>
          <mesh position={[0, 0.11, 0.04]} castShadow>
            <boxGeometry args={[0.06, 0.03, 0.26]} />
            <meshStandardMaterial color="#0f172a" roughness={0.3} />
          </mesh>
        </group>
      ))}
    </group>
  );
});

/* ═══════════════════════════════════════════════════════════════════
   3. Executive Desk
   ═══════════════════════════════════════════════════════════════════ */
export const ExecutiveDesk3D = memo(function ExecutiveDesk3D({
  position = [0, 0, 0],
  rotation = [0, 0, 0],
  color = "#451a03",
}: {
  position?: [number, number, number];
  rotation?: [number, number, number];
  color?: string;
}) {
  return (
    <group position={position} rotation={rotation}>
      {/* Main Wood Top */}
      <mesh geometry={geoExecDeskTop} position={[0, 0.75, 0]} castShadow receiveShadow>
        <meshStandardMaterial color={color} roughness={0.22} metalness={0.14} />
      </mesh>
      {/* Leather Inlay Pad */}
      <mesh geometry={geoExecInlay} position={[0, 0.795, 0]}>
        <meshStandardMaterial color="#eee8dc" roughness={0.55} />
      </mesh>
      {/* Executive Keyboard & Mouse */}
      <group position={[0, 0.805, -0.23]}>
        <mesh geometry={geoKeyboard}>
          <meshStandardMaterial color="#0f172a" roughness={0.4} />
        </mesh>
        <mesh geometry={geoKeys} position={[0, 0.008, 0]}>
          <meshStandardMaterial color="#f59e0b" roughness={0.5} />
        </mesh>
      </group>
      <mesh geometry={geoMousepad} position={[0.38, 0.801, -0.23]}>
        <meshStandardMaterial color="#1c1917" roughness={0.8} />
      </mesh>
      <mesh geometry={geoMouse} position={[0.38, 0.81, -0.23]} castShadow>
        <meshStandardMaterial color="#f59e0b" roughness={0.3} metalness={0.6} />
      </mesh>
      {/* Modesty Panel & Side Pillars */}
      <mesh geometry={geoExecPanel} position={[0, 0.38, 0.46]} castShadow>
        <meshStandardMaterial color={color} roughness={0.3} />
      </mesh>
      {[-1.0, 1.0].map((x) => (
        <mesh key={x} geometry={geoExecPillar} position={[x, 0.38, 0]} castShadow>
          <meshStandardMaterial color={color} roughness={0.3} />
        </mesh>
      ))}
      {/* Brass Executive Lamp */}
      <group position={[-0.75, 0.8, -0.3]}>
        <mesh position={[0, 0.02, 0]} castShadow>
          <cylinderGeometry args={[0.07, 0.09, 0.03, 12]} />
          <meshStandardMaterial color="#f59e0b" metalness={0.9} roughness={0.2} />
        </mesh>
        <mesh position={[0, 0.2, 0]} rotation={[0, 0, 0.2]} castShadow>
          <cylinderGeometry args={[0.018, 0.018, 0.32, 8]} />
          <meshStandardMaterial color="#f59e0b" metalness={0.9} roughness={0.2} />
        </mesh>
        <mesh position={[0.05, 0.34, 0]} rotation={[0, 0, -0.3]} castShadow>
          <coneGeometry args={[0.09, 0.14, 12]} />
          <meshStandardMaterial color="#b45309" roughness={0.3} />
        </mesh>
        <pointLight position={[0.07, 0.28, 0]} color="#fbbf24" intensity={1.8} distance={2.0} />
      </group>
    </group>
  );
});

/* ═══════════════════════════════════════════════════════════════════
   4. Conference Table
   ═══════════════════════════════════════════════════════════════════ */
export const ConferenceTable3D = memo(function ConferenceTable3D({
  position = [0, 0, 0],
}: {
  position?: [number, number, number];
}) {
  return (
    <group position={position}>
      {/* Premium Wood Conference Tabletop */}
      <mesh geometry={geoConfTop} position={[0, 0.74, 0]} castShadow receiveShadow>
        <meshStandardMaterial color="#2d1f3d" roughness={0.25} metalness={0.4} />
      </mesh>
      {/* Metallic Edge Trim */}
      <mesh geometry={geoConfTrim} position={[0, 0.74, 0]}>
        <meshStandardMaterial color="#8b5cf6" roughness={0.4} metalness={0.8} />
      </mesh>

      {/* Water Bottles & Notebook Props */}
      {[-0.8, 0, 0.8].map((x) => (
        <group key={x} position={[x, 0.77, 0.5]}>
          <mesh castShadow>
            <cylinderGeometry args={[0.03, 0.03, 0.12, 8]} />
            <meshStandardMaterial color="#38bdf8" transparent opacity={0.7} />
          </mesh>
          <mesh position={[0.15, 0, 0]} castShadow>
            <boxGeometry args={[0.12, 0.008, 0.18]} />
            <meshStandardMaterial color="#334155" roughness={0.6} />
          </mesh>
        </group>
      ))}

      {/* Base Pedestals */}
      {[-1.1, 1.1].map((x) => (
        <mesh key={x} geometry={geoConfPedestal} position={[x, 0.36, 0]} castShadow>
          <meshStandardMaterial color="#312e81" roughness={0.4} metalness={0.7} />
        </mesh>
      ))}
    </group>
  );
});

/* ═══════════════════════════════════════════════════════════════════
   5. Whiteboard
   ═══════════════════════════════════════════════════════════════════ */
export const Whiteboard3D = memo(function Whiteboard3D({
  position = [0, 0, 0],
  rotation = [0, 0, 0],
  title = "WHITEBOARD",
}: {
  position?: [number, number, number];
  rotation?: [number, number, number];
  title?: string;
}) {
  return (
    <group position={position} rotation={rotation}>
      {/* Metallic Frame */}
      <mesh position={[0, 1.2, 0]} castShadow>
        <boxGeometry args={[1.8, 1.1, 0.04]} />
        <meshStandardMaterial color="#64748b" roughness={0.3} metalness={0.8} />
      </mesh>
      {/* Whiteboard Surface */}
      <mesh position={[0, 1.2, 0.022]}>
        <planeGeometry args={[1.72, 1.02]} />
        <meshStandardMaterial color="#f8fafc" roughness={0.2} />
      </mesh>
      {/* Header Marker Text */}
      <Text
        position={[0, 1.58, 0.03]}
        fontSize={0.07}
        color="#0284c7"
        anchorX="center"
        anchorY="middle"
      >
        {title}
      </Text>
      {/* Marker Tray */}
      <mesh position={[0, 0.63, 0.04]} castShadow>
        <boxGeometry args={[1.7, 0.03, 0.06]} />
        <meshStandardMaterial color="#334155" />
      </mesh>
    </group>
  );
});

/* ═══════════════════════════════════════════════════════════════════
   6. Room Wall Signage
   ═══════════════════════════════════════════════════════════════════ */
export const RoomSign3D = memo(function RoomSign3D({
  position = [0, 0, 0],
  rotation = [0, 0, 0],
  title = "ROOM",
  color = "#38bdf8",
}: {
  position?: [number, number, number];
  rotation?: [number, number, number];
  title?: string;
  color?: string;
}) {
  return (
    <group position={position} rotation={rotation}>
      {/* Wall Header Mounting Plate */}
      <mesh castShadow position={[0, 0, 0]}>
        <boxGeometry args={[1.5, 0.28, 0.04]} />
        <meshStandardMaterial color="#0f172a" roughness={0.3} metalness={0.8} />
      </mesh>
      {/* Accent Edge Trim */}
      <mesh position={[0, 0, 0.022]}>
        <boxGeometry args={[1.46, 0.24, 0.01]} />
        <meshStandardMaterial color={color} roughness={0.2} metalness={0.6} />
      </mesh>
      {/* Physical 3D Signage Text */}
      <Text
        position={[0, 0, 0.035]}
        fontSize={0.095}
        color="#ffffff"
        anchorX="center"
        anchorY="middle"
        letterSpacing={0.06}
      >
        {title}
      </Text>
    </group>
  );
});

/* ═══════════════════════════════════════════════════════════════════
   7. Office Plant
   ═══════════════════════════════════════════════════════════════════ */
export const Plant3D = memo(function Plant3D({
  position = [0, 0, 0],
  scale = 1,
}: {
  position?: [number, number, number];
  scale?: number;
}) {
  return (
    <group position={position} scale={scale}>
      {/* Ceramic Pot */}
      <mesh geometry={geoPot} position={[0, 0.28, 0]} castShadow>
        <meshStandardMaterial color="#78350f" roughness={0.6} />
      </mesh>
      {/* Leaves Cluster (Shared Dodecahedron Geometries) */}
      <mesh geometry={geoLeaves1} position={[0, 0.82, 0]} scale={[0.38, 0.48, 0.38]} castShadow>
        <meshStandardMaterial color="#15803d" roughness={0.5} />
      </mesh>
      <mesh geometry={geoLeaves2} position={[0.12, 0.72, 0.12]} scale={[0.28, 0.38, 0.28]} castShadow>
        <meshStandardMaterial color="#15803d" roughness={0.5} />
      </mesh>
      <mesh geometry={geoLeaves3} position={[-0.12, 0.88, -0.06]} scale={[0.3, 0.4, 0.3]} castShadow>
        <meshStandardMaterial color="#15803d" roughness={0.5} />
      </mesh>
    </group>
  );
});

/* ═══════════════════════════════════════════════════════════════════
   8. Bookshelf
   ═══════════════════════════════════════════════════════════════════ */
export const Bookshelf3D = memo(function Bookshelf3D({
  position = [0, 0, 0],
  rotation = [0, 0, 0],
}: {
  position?: [number, number, number];
  rotation?: [number, number, number];
}) {
  return (
    <group position={position} rotation={rotation}>
      {/* Outer Case — Warm Dark Wood */}
      <mesh position={[0, 1.1, 0]} castShadow receiveShadow>
        <boxGeometry args={[1.2, 2.2, 0.4]} />
        <meshStandardMaterial color="#3d2b1a" roughness={0.4} />
      </mesh>
      {/* Shelves & Books */}
      {[0.45, 0.9, 1.35, 1.8].map((y, i) => (
        <group key={i} position={[0, y, 0.02]}>
          <mesh castShadow>
            <boxGeometry args={[1.12, 0.04, 0.36]} />
            <meshStandardMaterial color="#1e293b" roughness={0.3} />
          </mesh>
          {[-0.4, -0.2, 0.1, 0.3].map((x, bIdx) => (
            <mesh key={bIdx} position={[x, 0.15, 0]} castShadow>
              <boxGeometry args={[0.08, 0.26, 0.28]} />
              <meshStandardMaterial
                color={bIdx % 2 === 0 ? "#0284c7" : bIdx % 3 === 0 ? "#f59e0b" : "#10b981"}
                roughness={0.4}
              />
            </mesh>
          ))}
        </group>
      ))}
    </group>
  );
});

/* ═══════════════════════════════════════════════════════════════════
   9. Ceiling Light Fixture (Optimized shadow-free point lights)
   ═══════════════════════════════════════════════════════════════════ */
export const CeilingLight3D = memo(function CeilingLight3D({
  position = [0, 2.3, 0],
  type = "DEV",
  color = "#ffffff",
}: {
  position?: [number, number, number];
  type?: "CEO" | "DEV" | "MKT" | "FIN" | "BOARDROOM";
  color?: string;
}) {
  return (
    <group position={position}>
      {type === "DEV" && (
        <group>
          <mesh castShadow position={[0, 0, 0]}>
            <boxGeometry args={[2.8, 0.06, 0.15]} />
            <meshStandardMaterial color="#334155" roughness={0.3} metalness={0.8} />
          </mesh>
          <mesh position={[0, -0.035, 0]}>
            <boxGeometry args={[2.7, 0.01, 0.12]} />
            <meshBasicMaterial color="#38bdf8" />
          </mesh>
          <pointLight position={[0, -0.1, 0]} color={color} intensity={2.2} distance={6} />
        </group>
      )}

      {type === "MKT" && (
        <group>
          {[-0.8, 0.8].map((x) => (
            <group key={x} position={[x, 0, 0]}>
              <mesh position={[0, 0.15, 0]}>
                <cylinderGeometry args={[0.01, 0.01, 0.3, 8]} />
                <meshStandardMaterial color="#0f172a" />
              </mesh>
              <mesh position={[0, 0, 0]} castShadow>
                <coneGeometry args={[0.16, 0.2, 12]} />
                <meshStandardMaterial color="#be185d" roughness={0.3} />
              </mesh>
              <pointLight position={[0, -0.1, 0]} color={color} intensity={2.0} distance={5} />
            </group>
          ))}
        </group>
      )}

      {type === "FIN" && (
        <group>
          <mesh position={[0, 0, 0]}>
            <boxGeometry args={[2.4, 0.04, 1.2]} />
            <meshStandardMaterial color="#0f172a" roughness={0.4} />
          </mesh>
          <mesh position={[0, -0.02, 0]}>
            <boxGeometry args={[2.3, 0.01, 1.1]} />
            <meshBasicMaterial color="#34d399" />
          </mesh>
          <pointLight position={[0, -0.1, 0]} color={color} intensity={2.2} distance={6} />
        </group>
      )}

      {type === "CEO" && (
        <group>
          <mesh position={[0, 0, 0]}>
            <boxGeometry args={[1.2, 0.03, 0.3]} />
            <meshStandardMaterial color="#334155" roughness={0.3} metalness={0.8} />
          </mesh>
          <mesh position={[0, -0.015, 0]}>
            <boxGeometry args={[1.1, 0.01, 0.24]} />
            <meshBasicMaterial color="#fbbf24" />
          </mesh>
          <pointLight position={[0, -0.1, 0]} color={color} intensity={2.4} distance={7} />
        </group>
      )}

      {type === "BOARDROOM" && (
        <group>
          <mesh rotation={[Math.PI / 2, 0, 0]} castShadow>
            <torusGeometry args={[0.9, 0.04, 10, 24]} />
            <meshStandardMaterial color="#8b5cf6" roughness={0.3} metalness={0.8} />
          </mesh>
          <mesh rotation={[Math.PI / 2, 0, 0]} position={[0, -0.01, 0]}>
            <torusGeometry args={[0.9, 0.025, 10, 24]} />
            <meshBasicMaterial color="#a78bfa" />
          </mesh>
          <pointLight position={[0, -0.1, 0]} color={color} intensity={3.0} distance={7} />
        </group>
      )}
    </group>
  );
});
