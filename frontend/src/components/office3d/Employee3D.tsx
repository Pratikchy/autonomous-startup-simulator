import { useRef, useMemo, memo } from "react";
import { useFrame } from "@react-three/fiber";
import { Html } from "@react-three/drei";
import * as THREE from "three";
import type { EmployeeInfo } from "./officeLayout";

export type EmployeeActivity =
  | "IDLE"
  | "TYPING"
  | "READING"
  | "THINKING"
  | "WALKING"
  | "TALKING"
  | "PRESENTING"
  | "DEBATING"
  | "BLOCKED"
  | "MEETING";

interface Employee3DProps {
  info: EmployeeInfo;
  activity: EmployeeActivity;
  inBoardroom: boolean;
  isCurrentSpeaker: boolean;
  onSelectDepartment?: (dept: string) => void;
}

// Asynchronous time offsets per employee for unique, natural office work loops
const EMPLOYEE_TIME_OFFSETS: Record<string, number> = {
  "emp-ceo": 0.0,
  "emp-dev-1": 1.45,
  "emp-dev-2": 2.85,
  "emp-mkt-1": 0.95,
  "emp-mkt-2": 3.4,
  "emp-fin-1": 1.8,
  "emp-fin-2": 4.1,
};

/* ═══════════════════════════════════════════════════════════════════
   DETAILED CHARACTER STYLE SPECS (Deterministic per employee ID)
   ═══════════════════════════════════════════════════════════════════ */
interface CharacterSpec {
  heightScale: number;
  shoulderWidth: number;
  hairStyle: "A" | "B" | "C" | "D" | "E" | "F";
  hairColor: string;
  jacketColor?: string;
  shirtColor: string;
  accentColor: string;
  pantsColor: string;
  hasGlasses?: boolean;
}

const EMPLOYEE_SPECS: Record<string, CharacterSpec> = {
  "emp-ceo": {
    heightScale: 1.04,
    shoulderWidth: 1.02,
    hairStyle: "A",
    hairColor: "#271c19",
    jacketColor: "#1e293b", // Navy executive blazer
    shirtColor: "#fef3c7",  // Warm cream shirt
    accentColor: "#f59e0b", // Gold CEO lapel badge
    pantsColor: "#0f172a",
  },
  "emp-dev-1": {
    heightScale: 0.98,
    shoulderWidth: 0.98,
    hairStyle: "B",
    hairColor: "#1e293b",
    jacketColor: "#334155", // Slate tech sweater
    shirtColor: "#e0f2fe",  // Sky blue shirt
    accentColor: "#06b6d4", // Cyan tech lanyard
    pantsColor: "#1e293b",
    hasGlasses: true,
  },
  "emp-dev-2": {
    heightScale: 1.02,
    shoulderWidth: 1.04,
    hairStyle: "D",
    hairColor: "#451a03",
    shirtColor: "#0f172a",  // Dark tech tee
    accentColor: "#0891b2", // Cyan badge
    pantsColor: "#334155",
  },
  "emp-mkt-1": {
    heightScale: 0.96,
    shoulderWidth: 0.94,
    hairStyle: "C",
    hairColor: "#78350f",
    jacketColor: "#831843", // Deep rose blazer
    shirtColor: "#fce7f3",  // Soft pink blouse
    accentColor: "#ec4899", // Magenta accent
    pantsColor: "#1e293b",
  },
  "emp-mkt-2": {
    heightScale: 1.0,
    shoulderWidth: 0.96,
    hairStyle: "E",
    hairColor: "#1c1917",
    shirtColor: "#be185d",  // Burgundy top
    accentColor: "#f472b6", // Pink lanyard
    pantsColor: "#1e293b",
  },
  "emp-fin-1": {
    heightScale: 1.02,
    shoulderWidth: 1.02,
    hairStyle: "F",
    hairColor: "#292524",
    jacketColor: "#064e3b", // Forest green tailored vest
    shirtColor: "#f0fdf4",  // Crisp white shirt
    accentColor: "#10b981", // Emerald green tie/accent
    pantsColor: "#0f172a",
  },
  "emp-fin-2": {
    heightScale: 0.97,
    shoulderWidth: 0.96,
    hairStyle: "A",
    hairColor: "#451a03",
    jacketColor: "#1e293b", // Charcoal cardigan
    shirtColor: "#d1fae5",  // Sage collared shirt
    accentColor: "#059669", // Green badge
    pantsColor: "#1e293b",
  },
};

/* ═══════════════════════════════════════════════════════════════════
   SHARED GEOMETRY CONSTANTS (Zero GC allocations)
   ═══════════════════════════════════════════════════════════════════ */
const geoHead = new THREE.SphereGeometry(0.11, 14, 14);
const geoNeck = new THREE.CylinderGeometry(0.04, 0.045, 0.08, 10);
const geoEar = new THREE.BoxGeometry(0.02, 0.04, 0.025);
const geoEye = new THREE.SphereGeometry(0.014, 6, 6);
const geoEyebrow = new THREE.BoxGeometry(0.035, 0.008, 0.008);

const geoChest = new THREE.BoxGeometry(0.28, 0.22, 0.16);
const geoWaist = new THREE.BoxGeometry(0.25, 0.16, 0.14);
const geoPelvis = new THREE.BoxGeometry(0.24, 0.08, 0.15);
const geoCollar = new THREE.BoxGeometry(0.12, 0.06, 0.02);
const geoBadge = new THREE.BoxGeometry(0.045, 0.065, 0.008);
const geoLanyard = new THREE.CylinderGeometry(0.004, 0.004, 0.22, 6);

const geoUpperArm = new THREE.CylinderGeometry(0.034, 0.03, 0.22, 10);
const geoElbow = new THREE.SphereGeometry(0.032, 8, 8);
const geoForearm = new THREE.CylinderGeometry(0.03, 0.025, 0.22, 10);
const geoHand = new THREE.BoxGeometry(0.045, 0.02, 0.06);

const geoThigh = new THREE.CylinderGeometry(0.042, 0.036, 0.28, 10);
const geoKnee = new THREE.SphereGeometry(0.038, 8, 8);
const geoCalf = new THREE.CylinderGeometry(0.036, 0.032, 0.3, 10);
const geoShoe = new THREE.BoxGeometry(0.08, 0.055, 0.15);

/* Hairstyles A through F */
const geoHairStyleA = new THREE.SphereGeometry(0.118, 12, 12);
const geoHairStyleB = new THREE.BoxGeometry(0.14, 0.06, 0.16);
const geoHairStyleC = new THREE.DodecahedronGeometry(0.115, 1);
const geoHairStyleD = new THREE.SphereGeometry(0.122, 12, 12);
const geoHairBun = new THREE.SphereGeometry(0.045, 8, 8);
const geoHairCurly = new THREE.DodecahedronGeometry(0.125, 1);

// Shared scratch vectors for allocation-free vector math inside useFrame
const _scratchDir = new THREE.Vector3();

function Employee3DComponent({
  info,
  activity,
  inBoardroom,
  isCurrentSpeaker,
  onSelectDepartment,
}: Employee3DProps) {
  const groupRef = useRef<THREE.Group>(null);
  const headRef = useRef<THREE.Group>(null);
  const torsoRef = useRef<THREE.Group>(null);
  const leftArmRef = useRef<THREE.Group>(null);
  const rightArmRef = useRef<THREE.Group>(null);
  const leftLegRef = useRef<THREE.Group>(null);
  const rightLegRef = useRef<THREE.Group>(null);
  const leftThighRef = useRef<THREE.Group>(null);
  const rightThighRef = useRef<THREE.Group>(null);

  const spec = EMPLOYEE_SPECS[info.id] || EMPLOYEE_SPECS["emp-ceo"];
  const timeOffset = EMPLOYEE_TIME_OFFSETS[info.id] || 0.0;

  // Waypoints state (Desk -> Door -> Boardroom)
  const currentPos = useRef(
    new THREE.Vector3(
      inBoardroom ? info.boardroomPos.x : info.deskPos.x,
      inBoardroom ? info.boardroomPos.y : info.deskPos.y,
      inBoardroom ? info.boardroomPos.z : info.deskPos.z
    )
  );
  const currentWaypointIndex = useRef(inBoardroom ? 2 : 2); // Start at final destination waypoint

  const prevInBoardroom = useRef(inBoardroom);
  if (prevInBoardroom.current !== inBoardroom) {
    currentWaypointIndex.current = 0;
    prevInBoardroom.current = inBoardroom;
  }

  const pathWaypoints = useMemo(() => {
    if (inBoardroom) {
      return [
        new THREE.Vector3(info.deskPos.x, info.deskPos.y, info.deskPos.z),
        new THREE.Vector3(info.doorwayPos.x, info.doorwayPos.y, info.doorwayPos.z),
        new THREE.Vector3(info.boardroomPos.x, info.boardroomPos.y, info.boardroomPos.z),
      ];
    } else {
      return [
        new THREE.Vector3(info.boardroomPos.x, info.boardroomPos.y, info.boardroomPos.z),
        new THREE.Vector3(info.doorwayPos.x, info.doorwayPos.y, info.doorwayPos.z),
        new THREE.Vector3(info.deskPos.x, info.deskPos.y, info.deskPos.z),
      ];
    }
  }, [inBoardroom, info]);

  const targetRotation = inBoardroom ? info.boardroomRotation : info.deskRotation;

  useFrame(({ clock }, delta) => {
    const rawTime = clock.getElapsedTime();
    const t = rawTime + timeOffset;
    if (!groupRef.current) return;

    // 1. Waypoint Path Navigation (Smooth Walking Lerp)
    const targetPoint = pathWaypoints[currentWaypointIndex.current] || pathWaypoints[pathWaypoints.length - 1];
    const distToPoint = currentPos.current.distanceTo(targetPoint);

    if (distToPoint > 0.15) {
      currentPos.current.lerp(targetPoint, Math.min(delta * 4.5, 0.2));
      groupRef.current.position.copy(currentPos.current);

      _scratchDir.copy(targetPoint).sub(currentPos.current).normalize();
      if (_scratchDir.lengthSq() > 0.001) {
        const targetAngle = Math.atan2(_scratchDir.x, _scratchDir.z);
        groupRef.current.rotation.y = THREE.MathUtils.lerp(
          groupRef.current.rotation.y,
          targetAngle,
          delta * 9
        );
      }

      // Walking leg swing posture
      const walkCycle = Math.sin(t * 14);
      if (leftThighRef.current) leftThighRef.current.rotation.x = walkCycle * 0.6;
      if (rightThighRef.current) rightThighRef.current.rotation.x = -walkCycle * 0.6;
      if (leftLegRef.current) leftLegRef.current.rotation.x = Math.max(0, -walkCycle * 0.4);
      if (rightLegRef.current) rightLegRef.current.rotation.x = Math.max(0, walkCycle * 0.4);

      if (leftArmRef.current) leftArmRef.current.rotation.x = -walkCycle * 0.45;
      if (rightArmRef.current) rightArmRef.current.rotation.x = walkCycle * 0.45;
    } else {
      if (currentWaypointIndex.current < pathWaypoints.length - 1) {
        currentWaypointIndex.current += 1;
      } else {
        groupRef.current.position.copy(targetPoint);
        groupRef.current.rotation.y = THREE.MathUtils.lerp(
          groupRef.current.rotation.y,
          targetRotation,
          delta * 7
        );

        // Seated anatomical posture (Thighs horizontal at Math.PI/2, Calves vertical at 0)
        if (leftThighRef.current) leftThighRef.current.rotation.x = Math.PI / 2;
        if (rightThighRef.current) rightThighRef.current.rotation.x = Math.PI / 2;
        if (leftLegRef.current) leftLegRef.current.rotation.x = 0;
        if (rightLegRef.current) rightLegRef.current.rotation.x = 0;

        // Subtle torso breathing
        if (torsoRef.current) {
          torsoRef.current.position.y = 0.62 + Math.sin(t * 1.5) * 0.004;
        }

        // 2. Realistic Work Activity Loops
        if (activity === "TYPING") {
          const cycle = (t * 1.8) % 10;

          if (cycle < 6.5) {
            // Micro-typing on keyboard (constrained to safe keyboard zone)
            const typeFast = Math.sin(t * 14);
            const typeSlow = Math.cos(t * 7);

            if (leftArmRef.current) {
              leftArmRef.current.rotation.x = -0.40 + typeFast * 0.02;
              leftArmRef.current.rotation.y = 0.08 + typeSlow * 0.02;
              leftArmRef.current.rotation.z = 0.04;
            }
            if (rightArmRef.current) {
              rightArmRef.current.rotation.x = -0.40 - typeFast * 0.02;
              rightArmRef.current.rotation.y = -0.08 - typeSlow * 0.02;
              rightArmRef.current.rotation.z = -0.04;
            }
            if (headRef.current) {
              headRef.current.rotation.x = 0.06 + Math.sin(t * 2.0) * 0.015;
              headRef.current.rotation.y = Math.sin(t * 1.0) * 0.03;
            }
          } else if (cycle < 8.5) {
            // Mouse movement & screen review
            const mouseMove = Math.sin(t * 4);
            if (leftArmRef.current) {
              leftArmRef.current.rotation.x = -0.38;
              leftArmRef.current.rotation.y = 0.05;
            }
            if (rightArmRef.current) {
              rightArmRef.current.rotation.x = -0.42;
              rightArmRef.current.rotation.y = -0.16 + mouseMove * 0.03;
            }
            if (headRef.current) {
              headRef.current.rotation.x = 0.04;
              headRef.current.rotation.y = 0.05 + mouseMove * 0.03;
            }
          } else {
            // Brief pause / posture review
            if (leftArmRef.current) {
              leftArmRef.current.rotation.x = -0.36;
              leftArmRef.current.rotation.y = 0.04;
            }
            if (rightArmRef.current) {
              rightArmRef.current.rotation.x = -0.36;
              rightArmRef.current.rotation.y = -0.04;
            }
            if (headRef.current) {
              headRef.current.rotation.x = -0.04 + Math.sin(t * 1.2) * 0.02;
              headRef.current.rotation.y = Math.cos(t * 0.8) * 0.04;
            }
          }
        } else if (activity === "READING" || activity === "THINKING") {
          if (leftArmRef.current) leftArmRef.current.rotation.x = -0.35;
          if (rightArmRef.current) rightArmRef.current.rotation.x = -0.55;
          if (headRef.current) {
            headRef.current.rotation.x = -0.1 + Math.sin(t * 1.8) * 0.03;
            headRef.current.rotation.y = Math.cos(t * 1.0) * 0.12;
          }
        } else if (activity === "TALKING" || activity === "PRESENTING" || (inBoardroom && isCurrentSpeaker)) {
          const speakGesture = Math.sin(t * 8);
          if (leftArmRef.current) leftArmRef.current.rotation.x = -0.45 + speakGesture * 0.15;
          if (rightArmRef.current) rightArmRef.current.rotation.x = -0.6 - speakGesture * 0.18;
          if (headRef.current) {
            headRef.current.rotation.y = Math.sin(t * 3.5) * 0.12;
            headRef.current.rotation.x = Math.cos(t * 2.5) * 0.07;
          }
        } else {
          if (leftArmRef.current) leftArmRef.current.rotation.x = -0.25 + Math.sin(t * 1.2) * 0.02;
          if (rightArmRef.current) rightArmRef.current.rotation.x = -0.25 + Math.cos(t * 1.2) * 0.02;
          if (headRef.current) {
            headRef.current.rotation.y = Math.sin(t * 0.8) * 0.05;
            headRef.current.rotation.x = Math.cos(t * 0.6) * 0.03;
          }
        }
      }
    }
  });

  return (
    <group
      ref={groupRef}
      scale={1.08 * spec.heightScale} // Anatomically aligned human scale relative to workstations, chairs (0.46m) and desks (0.75m)
      onClick={(e) => {
        e.stopPropagation();
        if (onSelectDepartment) onSelectDepartment(info.department);
      }}
    >
      {/* Boardroom Speaker Spotlight */}
      {inBoardroom && isCurrentSpeaker && (
        <pointLight position={[0, 1.8, 0]} color="#fbbf24" intensity={2.2} distance={3.2} />
      )}

      {/* Floating 3D Blocker Badge */}
      {activity === "BLOCKED" && (
        <Html position={[0, 1.45, 0]} center distanceFactor={8} zIndexRange={[10, 0]}>
          <div className="bg-rose-950/95 text-rose-300 backdrop-blur-md px-2.5 py-1 rounded-lg border border-rose-500/60 text-[11px] font-bold shadow-xl animate-bounce pointer-events-none text-center whitespace-nowrap">
            ⚠️ BLOCKED
          </div>
        </Html>
      )}

      {/* ═══════════════════════════════════════════════════════════
          HUMANOID CHARACTER ANATOMY
          Pelvis at Y=0.44m (sits directly on chair cushion Y=0.46m)
          ═══════════════════════════════════════════════════════════ */}
      <group position={[0, 0, 0]}>
        {/* 1. HEAD & NECK */}
        <group ref={headRef} position={[0, 0.94, 0.04]}>
          {/* Visible Neck */}
          <mesh geometry={geoNeck} position={[0, -0.06, 0]} castShadow>
            <meshStandardMaterial color="#fed7aa" roughness={0.5} />
          </mesh>

          {/* Structured Head Shape */}
          <mesh geometry={geoHead} castShadow>
            <meshStandardMaterial color="#fed7aa" roughness={0.5} />
          </mesh>

          {/* Ears */}
          <mesh geometry={geoEar} position={[-0.115, 0, 0]} castShadow>
            <meshStandardMaterial color="#fbcfe8" roughness={0.5} />
          </mesh>
          <mesh geometry={geoEar} position={[0.115, 0, 0]} castShadow>
            <meshStandardMaterial color="#fbcfe8" roughness={0.5} />
          </mesh>

          {/* Facial Structure: Eyes & Eyebrows */}
          <mesh geometry={geoEye} position={[-0.04, 0.015, 0.105]}>
            <meshBasicMaterial color="#0f172a" />
          </mesh>
          <mesh geometry={geoEye} position={[0.04, 0.015, 0.105]}>
            <meshBasicMaterial color="#0f172a" />
          </mesh>
          <mesh geometry={geoEyebrow} position={[-0.04, 0.04, 0.102]}>
            <meshBasicMaterial color={spec.hairColor} />
          </mesh>
          <mesh geometry={geoEyebrow} position={[0.04, 0.04, 0.102]}>
            <meshBasicMaterial color={spec.hairColor} />
          </mesh>

          {/* Glasses for Dev lead */}
          {spec.hasGlasses && (
            <mesh position={[0, 0.015, 0.11]}>
              <boxGeometry args={[0.18, 0.04, 0.015]} />
              <meshStandardMaterial color="#0284c7" roughness={0.3} metalness={0.8} />
            </mesh>
          )}

          {/* Hairstyles A through F */}
          {spec.hairStyle === "A" && (
            <mesh geometry={geoHairStyleA} position={[0, 0.03, -0.015]} castShadow>
              <meshStandardMaterial color={spec.hairColor} roughness={0.8} />
            </mesh>
          )}
          {spec.hairStyle === "B" && (
            <group position={[0, 0.06, 0]}>
              <mesh geometry={geoHairStyleB} castShadow>
                <meshStandardMaterial color={spec.hairColor} roughness={0.7} />
              </mesh>
            </group>
          )}
          {spec.hairStyle === "C" && (
            <mesh geometry={geoHairStyleC} position={[0, 0.04, 0]} castShadow>
              <meshStandardMaterial color={spec.hairColor} roughness={0.9} />
            </mesh>
          )}
          {spec.hairStyle === "D" && (
            <mesh geometry={geoHairStyleD} position={[0, 0.02, -0.02]} castShadow>
              <meshStandardMaterial color={spec.hairColor} roughness={0.8} />
            </mesh>
          )}
          {spec.hairStyle === "E" && (
            <group position={[0, 0.03, 0]}>
              <mesh geometry={geoHairStyleA} castShadow>
                <meshStandardMaterial color={spec.hairColor} roughness={0.8} />
              </mesh>
              <mesh geometry={geoHairBun} position={[0, 0.02, -0.12]} castShadow>
                <meshStandardMaterial color={spec.hairColor} roughness={0.8} />
              </mesh>
            </group>
          )}
          {spec.hairStyle === "F" && (
            <mesh geometry={geoHairCurly} position={[0, 0.05, 0]} castShadow>
              <meshStandardMaterial color={spec.hairColor} roughness={0.9} />
            </mesh>
          )}
        </group>

        {/* 2. TORSO (Chest + Waist + Collar + Department Badge/Lanyard) */}
        <group ref={torsoRef} position={[0, 0.62, 0.04]}>
          {/* Upper Chest / Jacket */}
          <mesh geometry={geoChest} position={[0, 0.12, 0]} scale={[spec.shoulderWidth, 1, 1]} castShadow receiveShadow>
            <meshStandardMaterial color={spec.jacketColor || spec.shirtColor} roughness={0.4} />
          </mesh>
          {/* Lower Waist */}
          <mesh geometry={geoWaist} position={[0, -0.05, 0]} castShadow receiveShadow>
            <meshStandardMaterial color={spec.shirtColor} roughness={0.5} />
          </mesh>
          {/* Collar Accent */}
          <mesh geometry={geoCollar} position={[0, 0.22, 0.07]}>
            <meshStandardMaterial color="#ffffff" roughness={0.3} />
          </mesh>

          {/* Department ID Badge & Lanyard Accent */}
          <mesh geometry={geoBadge} position={[0.08 * spec.shoulderWidth, 0.06, 0.088]}>
            <meshStandardMaterial color={spec.accentColor} roughness={0.3} metalness={0.5} />
          </mesh>
          <mesh geometry={geoLanyard} position={[0, 0.14, 0.085]}>
            <meshStandardMaterial color={spec.accentColor} roughness={0.5} />
          </mesh>
        </group>

        {/* 3. ARMS & HANDS */}
        {/* Left Arm */}
        <group ref={leftArmRef} position={[-0.16 * spec.shoulderWidth, 0.74, 0.04]}>
          <mesh geometry={geoUpperArm} position={[0, -0.1, 0.04]} rotation={[-0.45, 0, 0]} castShadow>
            <meshStandardMaterial color={spec.jacketColor || spec.shirtColor} roughness={0.4} />
          </mesh>
          <mesh geometry={geoElbow} position={[0, -0.19, 0.07]} castShadow>
            <meshStandardMaterial color={spec.jacketColor || spec.shirtColor} roughness={0.4} />
          </mesh>
          <mesh geometry={geoForearm} position={[0, -0.23, 0.14]} rotation={[-0.65, 0, 0]} castShadow>
            <meshStandardMaterial color={spec.shirtColor} roughness={0.5} />
          </mesh>
          {/* Wrist / Hand over Keyboard */}
          <mesh geometry={geoHand} position={[0, -0.29, 0.21]} castShadow>
            <meshStandardMaterial color="#fed7aa" roughness={0.5} />
          </mesh>
        </group>

        {/* Right Arm */}
        <group ref={rightArmRef} position={[0.16 * spec.shoulderWidth, 0.74, 0.04]}>
          <mesh geometry={geoUpperArm} position={[0, -0.1, 0.04]} rotation={[-0.45, 0, 0]} castShadow>
            <meshStandardMaterial color={spec.jacketColor || spec.shirtColor} roughness={0.4} />
          </mesh>
          <mesh geometry={geoElbow} position={[0, -0.19, 0.07]} castShadow>
            <meshStandardMaterial color={spec.jacketColor || spec.shirtColor} roughness={0.4} />
          </mesh>
          <mesh geometry={geoForearm} position={[0, -0.23, 0.14]} rotation={[-0.65, 0, 0]} castShadow>
            <meshStandardMaterial color={spec.shirtColor} roughness={0.5} />
          </mesh>
          {/* Wrist / Hand over Keyboard */}
          <mesh geometry={geoHand} position={[0, -0.29, 0.21]} castShadow>
            <meshStandardMaterial color="#fed7aa" roughness={0.5} />
          </mesh>
        </group>

        {/* 4. ANATOMICAL LEGS (Pelvis at Y=0.44m, Thighs horizontal, Calves vertical, Feet at floor) */}
        <group position={[0, 0.44, 0.04]}>
          {/* Hips / Pelvis sitting directly on chair cushion */}
          <mesh geometry={geoPelvis} position={[0, 0, 0]} castShadow>
            <meshStandardMaterial color={spec.pantsColor} roughness={0.5} />
          </mesh>

          {/* Left Leg */}
          <group position={[-0.08, 0, 0]}>
            <group ref={leftThighRef} rotation={[Math.PI / 2, 0, 0]}>
              {/* Thigh extending forward under desk */}
              <mesh geometry={geoThigh} position={[0, 0.14, 0]} castShadow>
                <meshStandardMaterial color={spec.pantsColor} roughness={0.5} />
              </mesh>
              {/* Knee Joint */}
              <mesh geometry={geoKnee} position={[0, 0.28, 0]} castShadow>
                <meshStandardMaterial color={spec.pantsColor} roughness={0.5} />
              </mesh>
            </group>
            {/* Lower Leg & Shoe (dropping from Knee at Z=0.28m) */}
            <group ref={leftLegRef} position={[0, 0, 0.28]}>
              <mesh geometry={geoCalf} position={[0, -0.16, 0]} castShadow>
                <meshStandardMaterial color={spec.pantsColor} roughness={0.5} />
              </mesh>
              <mesh geometry={geoShoe} position={[0, -0.32, 0.03]} castShadow>
                <meshStandardMaterial color="#0f172a" roughness={0.3} />
              </mesh>
            </group>
          </group>

          {/* Right Leg */}
          <group position={[0.08, 0, 0]}>
            <group ref={rightThighRef} rotation={[Math.PI / 2, 0, 0]}>
              {/* Thigh extending forward under desk */}
              <mesh geometry={geoThigh} position={[0, 0.14, 0]} castShadow>
                <meshStandardMaterial color={spec.pantsColor} roughness={0.5} />
              </mesh>
              {/* Knee Joint */}
              <mesh geometry={geoKnee} position={[0, 0.28, 0]} castShadow>
                <meshStandardMaterial color={spec.pantsColor} roughness={0.5} />
              </mesh>
            </group>
            {/* Lower Leg & Shoe (dropping from Knee at Z=0.28m) */}
            <group ref={rightLegRef} position={[0, 0, 0.28]}>
              <mesh geometry={geoCalf} position={[0, -0.16, 0]} castShadow>
                <meshStandardMaterial color={spec.pantsColor} roughness={0.5} />
              </mesh>
              <mesh geometry={geoShoe} position={[0, -0.32, 0.03]} castShadow>
                <meshStandardMaterial color="#0f172a" roughness={0.3} />
              </mesh>
            </group>
          </group>
        </group>
      </group>
    </group>
  );
}

export default memo(Employee3DComponent);
