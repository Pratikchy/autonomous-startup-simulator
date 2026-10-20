import type { AgentTask, ExecutionState, ExecutionTask, Startup } from "../types";

export type TaskLifecycle = "PLANNING" | "EXECUTION";

/**
 * Determines whether the startup is in the autonomous execution lifecycle stage.
 * Execution has started if:
 *  - the startup's currentPhase is "EXECUTION"
 *  - the executionState is running, paused, or completed
 *  - or execution metrics/tasks exist (e.g. tasks generated or progress > 0)
 */
export function isExecutionLifecycle(
  startup: Startup | null,
  executionState: ExecutionState | null
): boolean {
  if (startup?.currentPhase === "EXECUTION") {
    return true;
  }
  if (!executionState) {
    return false;
  }
  return (
    executionState.isRunning ||
    executionState.isPaused ||
    executionState.isCompleted ||
    (executionState.tasks?.length ?? 0) > 0 ||
    executionState.overallProgress > 0 ||
    executionState.mvpProgress > 0
  );
}

export interface LifecycleTaskStats {
  completedCount: number;
  totalCount: number;
}

export function getPlanningTaskStats(tasks: AgentTask[] = []): LifecycleTaskStats {
  return {
    completedCount: tasks.filter((t) => t.status === "COMPLETED").length,
    totalCount: tasks.length,
  };
}

export function getExecutionTaskStats(tasks: ExecutionTask[] = []): LifecycleTaskStats {
  return {
    completedCount: tasks.filter((t) => t.status === "COMPLETED").length,
    totalCount: tasks.length,
  };
}
