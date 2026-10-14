package com.startupsimulator.agent;

import com.startupsimulator.tool.ToolArguments;
import com.startupsimulator.tool.ToolExecutionService;
import com.startupsimulator.tool.ToolParameter;
import com.startupsimulator.tool.ToolRegistry;
import com.startupsimulator.tool.ToolRequest;
import com.startupsimulator.tool.ToolResult;
import com.startupsimulator.tool.ToolSchema;
import com.startupsimulator.trace.ExecutionTraceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 4B: the generic, bounded, non-recursive tool-use reasoning loop shared by
 * every tool-capable agent. It is the ONLY place the "agent decided to use a
 * tool" story is implemented, and it contains <b>no</b> agent&rarr;tool routing:
 * it shows the LLM the whole {@link ToolRegistry} catalogue and lets the model
 * pick (or not pick) a tool entirely from its own structured {@link AgentToolStep}.
 *
 * <p>The loop, per invocation:
 * <ol>
 *   <li>call {@code llm.generateStructured(.., AgentToolStep.class)};</li>
 *   <li>FINAL &rarr; return the model's own answer (produced after it saw any
 *       results);</li>
 *   <li>USE_TOOL &rarr; build a {@link ToolRequest} from the model's chosen name
 *       and arguments, run it through {@link ToolExecutionService} (the real tool,
 *       the real events), append the real {@link ToolResult} to the history, and
 *       loop so the result enters the NEXT prompt;</li>
 *   <li>a malformed step is fed back as a controlled note (the model can recover);</li>
 *   <li>at most {@link #MAX_TOOL_CALLS} tools run; once the budget is spent the
 *       model is told to finalise and no further tool is executed.</li>
 * </ol>
 *
 * <p>Java never selects a tool, never executes one the model did not name, and
 * never writes the final answer: tool failures come back as reasoning context,
 * not crashes, and REAL-mode LLM/transport/parse failures stay explicit failures
 * (no fabrication), exactly as the single-shot path already behaves.
 */
@Service
public class AgentToolReasoner {

    /** Hard cap on genuine tool executions per agent invocation (no unbounded loop). */
    public static final int MAX_TOOL_CALLS = 3;

    private static final Logger log = LoggerFactory.getLogger(AgentToolReasoner.class);

    private final ToolRegistry registry;
    private final ToolExecutionService executionService;
    private final ExecutionTraceService traceService;

    public AgentToolReasoner(ToolRegistry registry, ToolExecutionService executionService) {
        this(registry, executionService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AgentToolReasoner(ToolRegistry registry, ToolExecutionService executionService, ExecutionTraceService traceService) {
        this.registry = registry;
        this.executionService = executionService;
        this.traceService = traceService;
    }

    /**
     * Run the bounded tool-use loop for one agent turn.
     *
     * @param llm             the single LLM seam (real provider or test double)
     * @param usesRealLlm     the authoritative-output gate; when false NO tool
     *                        selection is attempted and nothing is fabricated
     * @param personaSystem   the agent's persona/mandate system framing (the tool
     *                        protocol is appended here, in one place)
     * @param contextBlock    sections A–D (startup/context, CEO analysis, inbox,
     *                        sent) already composed by the agent
     * @param requestingAgent machine identity of the caller (e.g. an AgentType name)
     * @param startupId       the simulation id (required by the execution boundary)
     * @param ctx             the read-only simulation context passed to the tool
     */
    public ToolAssistedOutcome reason(LLMService llm, boolean usesRealLlm, String personaSystem,
                                      String contextBlock, String requestingAgent,
                                      Long startupId, StartupContext ctx) {
        // REAL-mode authority gate: genuine, LLM-driven selection only. We never
        // substitute a deterministic tool choice when no real model is available.
        if (!usesRealLlm) {
            return ToolAssistedOutcome.noRealProvider();
        }

        String systemPrompt = buildSystemPrompt(personaSystem);
        List<ToolInteraction> interactions = new ArrayList<>();
        int toolCalls = 0;
        int maxLlmCalls = MAX_TOOL_CALLS + 1; // N tool calls + one forced finalisation

        for (int call = 1; call <= maxLlmCalls; call++) {
            boolean mustFinalize = toolCalls >= MAX_TOOL_CALLS || call == maxLlmCalls;
            String userPrompt = buildUserPrompt(contextBlock, interactions, mustFinalize);

            AgentToolStep step;
            try {
                step = llm.generateStructured(systemPrompt, userPrompt, AgentToolStep.class);
            } catch (CeoAnalysisException | AgentAnalysisException e) {
                // Preserve existing REAL-mode semantics: an LLM/transport/parse
                // failure is an explicit failure, never fabricated reasoning.
                log.warn("Tool-reasoning LLM call failed for agent {} on startup {}: {}",
                        requestingAgent, startupId, e.getClass().getSimpleName());
                return ToolAssistedOutcome.llmFailure(interactions);
            }

            if (step == null || step.isUnknownAction()) {
                // Malformed shape: feed a controlled note back so the model can
                // recover on its next (bounded) step — never crash.
                interactions.add(ToolInteraction.malformed(interactions.size() + 1,
                        step == null ? "No structured step was returned. Reply with action USE_TOOL or FINAL."
                                : "Unknown action '" + step.action() + "'. Use action USE_TOOL or FINAL."));
                continue;
            }

            if (traceService != null) {
                traceService.recordLLMReasoning(startupId, null, requestingAgent, "TOOL_REASONING",
                        step.isFinal() ? "FINAL_RESPONSE" : ("USE_TOOL: " + step.trimmedToolName()),
                        java.util.Map.of("action", step.action() != null ? step.action() : "",
                                "toolName", step.trimmedToolName() != null ? step.trimmedToolName() : "",
                                "reason", step.reason() != null ? step.reason() : ""));
            }

            if (step.isFinal()) {
                return ToolAssistedOutcome.finalized(step.finalResponseOrEmpty(), interactions);
            }

            // USE_TOOL from here on.
            if (mustFinalize) {
                // Budget already spent (or this is the final slot): do NOT execute
                // another tool. Controlled termination, not a silent extra call.
                log.debug("Agent {} requested a tool past its budget; terminating without execution.",
                        requestingAgent);
                return ToolAssistedOutcome.budgetExhausted(interactions);
            }
            if (isBlank(step.trimmedToolName())) {
                interactions.add(ToolInteraction.malformed(interactions.size() + 1,
                        "A USE_TOOL step must include a non-empty 'toolName'."));
                continue;
            }

            // The model chose this tool and these arguments — Java only transports them.
            ToolRequest request = ToolRequest.of(startupId, requestingAgent,
                    step.trimmedToolName(), ToolArguments.of(step.argumentsOrEmpty()));
            ToolResult result = executionService.execute(request, ctx);
            interactions.add(ToolInteraction.executed(interactions.size() + 1, step, result));
            toolCalls++; // a genuine execution (success OR failure) consumes budget
        }

        // Ran out of LLM calls without a FINAL — controlled termination.
        return ToolAssistedOutcome.budgetExhausted(interactions);
    }

    // ---- Prompt composition (catalogue generated from the registry) ----------

    private String buildSystemPrompt(String personaSystem) {
        StringBuilder sb = new StringBuilder();
        if (personaSystem != null && !personaSystem.isBlank()) {
            sb.append(personaSystem.trim()).append("\n\n");
        }
        sb.append("=== TOOL USE PROTOCOL ===\n");
        sb.append("You may use tools to compute precise figures before you answer. Respond with "
                + "EXACTLY ONE JSON object and nothing else, in one of two shapes:\n");
        sb.append("1. To run a tool: "
                + "{\"action\":\"USE_TOOL\",\"toolName\":\"<name>\",\"reason\":\"<why>\",\"arguments\":{...}}\n");
        sb.append("   - Choose a toolName from the AVAILABLE TOOLS catalogue in the message below.\n");
        sb.append("   - The arguments object MUST match that tool's parameter schema (names, types, ranges).\n");
        sb.append("2. To finish: {\"action\":\"FINAL\",\"finalResponse\":\"<your analysis>\"}\n\n");
        sb.append("Rules:\n");
        sb.append("- Decide for yourself whether a tool is needed; you are never required to use one.\n");
        sb.append("- After a tool runs, its ACTUAL result is returned to you under TOOL INTERACTION HISTORY "
                + "for further reasoning. Never fabricate or guess a tool's output.\n");
        sb.append("- If a tool fails, you will see the failure; fix the arguments, try another tool, or answer "
                + "without it.\n");
        sb.append("- You may use at most ").append(MAX_TOOL_CALLS)
                .append(" tools per turn. When you have enough information, return action FINAL.\n");
        return sb.toString();
    }

    private String buildUserPrompt(String contextBlock, List<ToolInteraction> interactions, boolean mustFinalize) {
        StringBuilder sb = new StringBuilder();
        if (contextBlock != null && !contextBlock.isBlank()) {
            sb.append(contextBlock.trim()).append("\n\n");
        }
        sb.append(availableToolsBlock()).append('\n');
        sb.append(interactionHistoryBlock(interactions)).append('\n');
        sb.append("=== HOW TO RESPOND ===\n");
        if (mustFinalize) {
            sb.append("You have reached the tool-use limit for this turn. Respond now with "
                    + "action FINAL and your finalResponse, reasoning over the tool results above.\n");
        } else {
            sb.append("Reply with a single JSON object: action USE_TOOL to run one of the AVAILABLE TOOLS, "
                    + "or action FINAL with your finalResponse when you have enough information.\n");
        }
        return sb.toString();
    }

    private String availableToolsBlock() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== AVAILABLE TOOLS ===\n");
        List<ToolSchema> schemas = registry.listAvailable();
        if (schemas.isEmpty()) {
            sb.append("(no tools are available)\n");
            return sb.toString();
        }
        for (ToolSchema schema : schemas) {
            sb.append("- ").append(schema.toolName()).append(": ").append(schema.description()).append('\n');
            for (ToolParameter p : schema.parameters()) {
                sb.append("    • ").append(p.name())
                        .append(" (").append(p.type()).append(p.required() ? ", required" : ", optional");
                if (p.min() != null || p.max() != null) {
                    sb.append(", range [").append(p.min() == null ? "-∞" : p.min())
                            .append(", ").append(p.max() == null ? "∞" : p.max()).append(']');
                }
                sb.append(") — ").append(p.description()).append('\n');
            }
        }
        return sb.toString();
    }

    private String interactionHistoryBlock(List<ToolInteraction> interactions) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== TOOL INTERACTION HISTORY ===\n");
        if (interactions.isEmpty()) {
            sb.append("(no tools used yet this turn)\n");
            return sb.toString();
        }
        for (ToolInteraction it : interactions) {
            if (it.isMalformed()) {
                sb.append("Step #").append(it.index()).append(" was INVALID: ")
                        .append(it.malformedNote()).append('\n');
                continue;
            }
            sb.append("Tool call #").append(it.index()).append(": requested '").append(it.toolName())
                    .append("' with arguments ").append(it.arguments());
            if (it.reason() != null && !it.reason().isBlank()) {
                sb.append(" (reason: ").append(it.reason().trim()).append(')');
            }
            sb.append('\n');
            sb.append("    RESULT: ").append(renderResult(it.result())).append('\n');
        }
        return sb.toString();
    }

    private String renderResult(ToolResult result) {
        if (result.isSuccess()) {
            return "SUCCESS — " + String.valueOf(result.result());
        }
        return "FAILURE [" + result.errorCode() + "] " + result.errorMessage();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
