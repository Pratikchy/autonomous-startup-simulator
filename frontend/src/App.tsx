import { useCallback } from "react";
import { Routes, Route, useNavigate, useParams, Navigate } from "react-router-dom";
import { useSim } from "./store/simulationStore";
import { useEventStream } from "./hooks/useEventStream";
import LandingPage from "./pages/LandingPage";
import SimulatorPage from "./pages/SimulatorPage";

/** Wrapper that owns the SSE connection while a startup is active. */
function SimulatorPageWrapper() {
  const startup = useSim((s) => s.startup);
  const onEvent = useSim((s) => s.onEvent);
  const load = useSim((s) => s.load);
  const { id } = useParams<{ id: string }>();

  const numericId = id && !isNaN(Number(id)) ? Number(id) : null;
  if (numericId == null) {
    return <Navigate to="/" replace />;
  }
  const activeId = startup?.id ?? numericId;

  const handleReconnect = useCallback(() => {
    return load(activeId);
  }, [activeId, load]);

  const { connected } = useEventStream(activeId, onEvent, handleReconnect);

  return <SimulatorPage sseConnected={connected} startupId={numericId} />;
}

function LandingPageWrapper() {
  const navigate = useNavigate();
  return <LandingPage onReady={(id: number) => navigate(`/startups/${id}`)} />;
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<LandingPageWrapper />} />
      <Route path="/startups/:id" element={<SimulatorPageWrapper />} />
      {/* Catch-all: redirect unknown paths to landing */}
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

