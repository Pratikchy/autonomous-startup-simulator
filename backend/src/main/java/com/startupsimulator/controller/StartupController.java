package com.startupsimulator.controller;

import com.startupsimulator.dto.request.CreateStartupRequest;
import com.startupsimulator.dto.response.*;
import com.startupsimulator.model.Startup;
import com.startupsimulator.model.enums.AgentType;
import com.startupsimulator.orchestrator.StartupOrchestrator;
import com.startupsimulator.service.*;
import com.startupsimulator.dto.response.ExecutionTraceEntryDto;
import com.startupsimulator.trace.ExecutionTraceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Primary REST surface for the simulator. All authoritative state lives in the
 * backend; the frontend creates a startup, kicks off the simulation, and then
 * reacts to the event stream ({@link EventStreamController}) while polling these
 * read endpoints for the current snapshot.
 */
@RestController
@RequestMapping("/api/startups")
@RequiredArgsConstructor
public class StartupController {

    private final StartupService startupService;
    private final AgentService agentService;
    private final TaskService taskService;
    private final MessageService messageService;
    private final DebateService debateService;
    private final DecisionService decisionService;
    private final EventService eventService;
    private final BlueprintService blueprintService;
    private final StartupContextService contextService;
    private final StartupOrchestrator orchestrator;
    private final ExecutionTraceService traceService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StartupDto create(@Valid @RequestBody CreateStartupRequest request) {
        return StartupDto.from(startupService.createStartup(request));
    }

    @GetMapping
    public List<StartupDto> list() {
        return startupService.listStartups().stream().map(StartupDto::from).toList();
    }

    @GetMapping("/{id}")
    public StartupDto get(@PathVariable Long id) {
        return StartupDto.from(startupService.getStartup(id));
    }

    @PostMapping("/{id}/simulate")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseEntity<StartupDto> simulate(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1.0") double speed) {
        Startup startup = startupService.getStartup(id);
        orchestrator.simulate(id, speed);
        return ResponseEntity.accepted().body(StartupDto.from(startup));
    }

    /**
     * Re-run the CEO's strategic analysis (Phase 2A). Useful after a failed LLM
     * analysis: it runs the CEO agent again against the latest startup state and
     * emits fresh CEO/LLM events over SSE. Runs asynchronously.
     */
    @PostMapping("/{id}/agents/ceo/analyze")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseEntity<StartupDto> retryCeoAnalysis(@PathVariable Long id) {
        Startup startup = startupService.getStartup(id);
        orchestrator.retryCeoAnalysis(id);
        return ResponseEntity.accepted().body(StartupDto.from(startup));
    }

    /**
     * Re-run a single department's analysis (Phase 2B). Runs the Developer,
     * Marketing, or Finance agent again against the latest startup state — building
     * on the CEO's analysis and accumulated context — and emits fresh
     * AGENT_ANALYSIS_* / LLM events over SSE. Runs asynchronously.
     */
    @PostMapping("/{id}/agents/developer/analyze")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseEntity<StartupDto> retryDeveloperAnalysis(@PathVariable Long id) {
        return retryDepartment(id, AgentType.DEVELOPMENT);
    }

    @PostMapping("/{id}/agents/marketing/analyze")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseEntity<StartupDto> retryMarketingAnalysis(@PathVariable Long id) {
        return retryDepartment(id, AgentType.MARKETING);
    }

    @PostMapping("/{id}/agents/finance/analyze")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseEntity<StartupDto> retryFinanceAnalysis(@PathVariable Long id) {
        return retryDepartment(id, AgentType.FINANCE);
    }

    private ResponseEntity<StartupDto> retryDepartment(Long id, AgentType type) {
        Startup startup = startupService.getStartup(id);
        orchestrator.retryDepartmentAnalysis(id, type);
        return ResponseEntity.accepted().body(StartupDto.from(startup));
    }

    @GetMapping("/{id}/agents")
    public List<AgentDto> agents(@PathVariable Long id) {
        return agentService.findByStartup(id).stream().map(AgentDto::from).toList();
    }

    @GetMapping("/{id}/tasks")
    public List<AgentTaskDto> tasks(@PathVariable Long id) {
        return taskService.list(id).stream().map(AgentTaskDto::from).toList();
    }

    @GetMapping("/{id}/messages")
    public List<AgentMessageDto> messages(@PathVariable Long id) {
        return messageService.list(id).stream().map(AgentMessageDto::from).toList();
    }

    @GetMapping("/{id}/debates")
    public List<DebateDto> debates(@PathVariable Long id) {
        return debateService.listDtos(id);
    }

    @GetMapping("/{id}/decisions")
    public List<DecisionDto> decisions(@PathVariable Long id) {
        return decisionService.list(id).stream().map(DecisionDto::from).toList();
    }

    @GetMapping("/{id}/events")
    public List<StartupEventDto> events(@PathVariable Long id) {
        return eventService.history(id);
    }

    @GetMapping("/{id}/blueprint")
    public BlueprintDto blueprint(@PathVariable Long id) {
        return blueprintService.build(id);
    }

    /**
     * Aggregated department plans (technical / marketing / budget) plus their
     * per-analysis metadata (provider, failed flag, error). The frontend renders
     * the department panels and retry affordances from this single snapshot.
     */
    @GetMapping("/{id}/plans")
    public PlansDto plans(@PathVariable Long id) {
        return PlansDto.of(
                contextService.technicalPlan(id),
                contextService.marketingPlan(id),
                contextService.budget(id));
    }

    /**
     * CA3 Section A: First-class queryable execution trace endpoint.
     * Returns chronologically and sequentially ordered trace entries representing
     * genuine agent reasoning, messaging, tool calls, memory, decisions, replanning, and recovery.
     */
    @GetMapping("/{id}/trace")
    public List<ExecutionTraceEntryDto> trace(
            @PathVariable Long id,
            @RequestParam(required = false) String agent) {
        if (agent != null && !agent.isBlank()) {
            return traceService.getTraceByAgent(id, agent);
        }
        return traceService.getTrace(id);
    }
}

