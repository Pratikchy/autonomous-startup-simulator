package com.startupsimulator.tool;

import com.startupsimulator.agent.StartupContext;
import com.startupsimulator.model.enums.EventType;
import com.startupsimulator.service.EventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * THE central execution boundary for tools. Agents (in Phase 4B) will call this
 * service — never a {@link Tool} directly, and never {@link ToolRegistry} to run
 * one. Its job, for a single {@link ToolRequest}, is to:
 * <ol>
 *   <li>validate the request shape (non-null, has a startup, a requesting agent,
 *       and a tool name);</li>
 *   <li>resolve the tool from the {@link ToolRegistry} (unknown tool → controlled
 *       FAILURE, no event — nothing executed);</li>
 *   <li>emit {@code TOOL_EXECUTION_STARTED} only once a real tool is about to run;</li>
 *   <li>execute within a wall-clock bound on a worker thread (defense against a
 *       pathological tool), catching every failure mode;</li>
 *   <li>emit {@code TOOL_EXECUTION_SUCCEEDED} or {@code TOOL_EXECUTION_FAILED}
 *       reflecting the <em>actual</em> outcome; and</li>
 *   <li>return a structured {@link ToolResult} — a tool failure can never crash
 *       the simulation.</li>
 * </ol>
 *
 * <p>Events are emitted only when execution genuinely happened: a request that
 * fails pre-execution validation (null, missing fields, unknown tool) produces a
 * FAILURE result with no event, so no event ever claims an execution that did not
 * occur.
 *
 * <p>This layer deliberately contains NO tool-selection logic: it does not decide
 * which tool an agent should use. That is Phase 4B.
 */
import com.startupsimulator.model.enums.AgentType;
import com.startupsimulator.trace.ExecutionTraceService;

@Service
public class ToolExecutionService {

    /** Default wall-clock bound for a single tool execution. */
    public static final long DEFAULT_TIMEOUT_MILLIS = 5_000L;

    private final ToolRegistry registry;
    private final EventService eventService;
    private final ExecutionTraceService traceService;
    private final long timeoutMillis;
    private final ExecutorService executor;

    public ToolExecutionService(ToolRegistry registry, EventService eventService) {
        this(registry, eventService, null, DEFAULT_TIMEOUT_MILLIS);
    }

    public ToolExecutionService(ToolRegistry registry, EventService eventService, long timeoutMillis) {
        this(registry, eventService, null, timeoutMillis);
    }

    @Autowired
    public ToolExecutionService(ToolRegistry registry, EventService eventService, ExecutionTraceService traceService) {
        this(registry, eventService, traceService, DEFAULT_TIMEOUT_MILLIS);
    }

    public ToolExecutionService(ToolRegistry registry, EventService eventService, ExecutionTraceService traceService, long timeoutMillis) {
        this.registry = registry;
        this.eventService = eventService;
        this.traceService = traceService;
        this.timeoutMillis = timeoutMillis;
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "tool-exec");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Validate, resolve, and execute the request, always returning a structured
     * result. {@code context} is the read-only simulation context the tool may
     * consult; it may be null for context-free tools.
     */
    public ToolResult execute(ToolRequest request, StartupContext context) {
        // ---- 1. Request-shape validation (pre-execution; no events) ----------
        if (request == null) {
            return ToolResult.failure("<unknown>", "INVALID_REQUEST",
                    "Tool request must not be null.", null, 0L);
        }
        if (request.startupId() == null) {
            return ToolResult.failure(nullSafeName(request), "INVALID_REQUEST",
                    "Tool request must identify a startup.", request.requestId(), 0L);
        }
        if (isBlank(request.requestingAgent())) {
            return ToolResult.failure(nullSafeName(request), "INVALID_REQUEST",
                    "Tool request must identify the requesting agent.", request.requestId(), 0L);
        }
        if (isBlank(request.toolName())) {
            return ToolResult.failure("<unknown>", "INVALID_REQUEST",
                    "Tool request must name a tool.", request.requestId(), 0L);
        }

        // ---- 2. Resolve tool (unknown → controlled failure, nothing executed) -
        Optional<Tool> resolved = registry.find(request.toolName());
        if (resolved.isEmpty()) {
            return ToolResult.failure(request.toolName(), "UNKNOWN_TOOL",
                    "No tool is registered under the name '" + request.toolName() + "'.",
                    request.requestId(), 0L);
        }
        Tool tool = resolved.get();

        // ---- 3. Execution genuinely begins → STARTED event -------------------
        emit(EventType.TOOL_EXECUTION_STARTED, request,
                "Tool '" + tool.name() + "' execution started.", null);

        if (traceService != null) {
            AgentType agent = parseAgentType(request.requestingAgent());
            Map<String, Object> argsMap = request.arguments() != null ? request.arguments().asMap() : Map.of();
            traceService.recordToolCall(request.startupId(), null, agent, tool.name(), argsMap, "Agent requested tool execution");
        }

        // ---- 4. Bounded execution + outer guard (defense in depth) -----------
        ToolResult result = runBounded(tool, request, context);

        // ---- 5. Outcome event reflects the ACTUAL result ---------------------
        if (result.isSuccess()) {
            emit(EventType.TOOL_EXECUTION_SUCCEEDED, request,
                    "Tool '" + tool.name() + "' succeeded.",
                    Map.of("durationMillis", result.durationMillis()));
        } else {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("errorCode", result.errorCode());
            payload.put("durationMillis", result.durationMillis());
            emit(EventType.TOOL_EXECUTION_FAILED, request,
                    "Tool '" + tool.name() + "' failed: " + result.errorCode() + ".", payload);
        }

        if (traceService != null) {
            AgentType agent = parseAgentType(request.requestingAgent());
            String outputStr = result.isSuccess()
                    ? (result.result() != null ? result.result().toString() : "SUCCESS")
                    : result.errorMessage();
            traceService.recordToolResult(request.startupId(), null, agent, tool.name(), result.isSuccess(), outputStr);
        }

        return result;
    }

    private ToolResult runBounded(Tool tool, ToolRequest request, StartupContext context) {
        long start = System.nanoTime();
        Future<ToolResult> future = executor.submit((Callable<ToolResult>) () -> tool.execute(request, context));
        try {
            ToolResult r = future.get(timeoutMillis, TimeUnit.MILLISECONDS);
            // A well-behaved AbstractTool never returns null, but guard anyway.
            return r != null ? r : ToolResult.failure(tool.name(), "EXECUTION_ERROR",
                    "The tool returned no result.", request.requestId(), elapsedMillis(start));
        } catch (TimeoutException e) {
            future.cancel(true);
            return ToolResult.failure(tool.name(), "EXECUTION_TIMEOUT",
                    "The tool exceeded its execution time budget.",
                    request.requestId(), elapsedMillis(start));
        } catch (ExecutionException e) {
            // The tool threw past its own guard — contain it here. No stack trace exposed.
            return ToolResult.failure(tool.name(), "EXECUTION_ERROR",
                    "The tool failed to complete due to an internal error.",
                    request.requestId(), elapsedMillis(start));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolResult.failure(tool.name(), "EXECUTION_INTERRUPTED",
                    "The tool execution was interrupted.",
                    request.requestId(), elapsedMillis(start));
        }
    }

    private void emit(EventType type, ToolRequest request, String message, Map<String, Object> extra) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("toolName", request.toolName());
        payload.put("requestingAgent", request.requestingAgent());
        payload.put("requestId", request.requestId());
        if (extra != null) {
            payload.putAll(extra);
        }
        eventService.record(request.startupId(), type, message, payload);
    }

    private static String nullSafeName(ToolRequest request) {
        return isBlank(request.toolName()) ? "<unknown>" : request.toolName();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private static AgentType parseAgentType(String name) {
        if (name == null) return null;
        try {
            return AgentType.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
