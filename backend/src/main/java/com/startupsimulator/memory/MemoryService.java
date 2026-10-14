package com.startupsimulator.memory;

import com.startupsimulator.model.enums.AgentType;
import com.startupsimulator.model.enums.EventType;
import com.startupsimulator.service.EventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Phase 5A: the <b>boundary</b> of the persistent-memory subsystem (requirement 7).
 * Reasoning code talks only to this service with typed DTOs; it never sees
 * {@link AgentMemoryRepository} or {@link AgentMemory}. Responsibilities:
 * <ul>
 *   <li>validate a {@link CreateMemoryRequest} and reject malformed candidates with
 *       a coded {@link MemoryValidationException} — no malformed row ever reaches
 *       the DB (requirement 8);</li>
 *   <li>deterministic exact-duplicate suppression (requirement 9): an identical
 *       startup+scope+author+type+content candidate does not create a second row;</li>
 *   <li>persist, then emit {@link EventType#MEMORY_CREATED} <em>only after</em> the
 *       row exists (requirement 12);</li>
 *   <li>deterministic, scope-explicit retrieval (requirements 6/10/15/16/17).</li>
 * </ul>
 *
 * <p>Phase 5A is infrastructure only: nothing here is wired into agent reasoning,
 * no prompt injection, and memory creation is always an explicit caller action.
 */
import com.startupsimulator.trace.ExecutionTraceService;

@Slf4j
@Service
public class MemoryService {

    static final int MIN_IMPORTANCE = 1;
    static final int MAX_IMPORTANCE = 10;
    /** Guard against absurdly large content; meaningful memories are short. */
    static final int MAX_CONTENT_LENGTH = 10_000;
    /** Default cap for an unbounded "recent" request. */
    static final int DEFAULT_RECENT_LIMIT = 20;

    private final AgentMemoryRepository repository;
    private final EventService eventService;
    private final ExecutionTraceService traceService;

    public MemoryService(AgentMemoryRepository repository, EventService eventService) {
        this(repository, eventService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public MemoryService(AgentMemoryRepository repository, EventService eventService, ExecutionTraceService traceService) {
        this.repository = repository;
        this.eventService = eventService;
        this.traceService = traceService;
    }

    // ---- Creation -----------------------------------------------------------

    /**
     * Validate, de-duplicate and persist a memory. Returns the persisted (or the
     * pre-existing duplicate) record. Throws {@link MemoryValidationException} for
     * an invalid request; never writes a malformed row.
     */
    @Transactional
    public MemoryRecord save(CreateMemoryRequest request) {
        validate(request);
        String content = request.normalizedContent();

        // Deterministic exact-duplicate suppression — no second row, no event.
        if (repository.existsByStartupIdAndScopeAndAgentTypeAndMemoryTypeAndContent(
                request.startupId(), request.scope(), request.agentType(), request.memoryType(), content)) {
            log.debug("Duplicate memory suppressed for startup={} agent={} type={}",
                    request.startupId(), request.agentType(), request.memoryType());
            AgentMemory existing = repository
                    .findFirstByStartupIdAndScopeAndAgentTypeAndMemoryTypeAndContentOrderByIdAsc(
                            request.startupId(), request.scope(), request.agentType(),
                            request.memoryType(), content);
            return MemoryRecord.from(existing);
        }

        AgentMemory saved = repository.save(new AgentMemory(
                request.startupId(), request.agentType(), request.scope(),
                request.memoryType(), content, request.importance(),
                request.source(), request.sourceReference()));

        // Emitted ONLY now that a real row with a DB-assigned id exists.
        eventService.record(request.startupId(), EventType.MEMORY_CREATED,
                request.agentType().getDisplayName() + " recorded a " + request.memoryType()
                        + " memory (" + request.scope() + ")",
                Map.of("memoryId", saved.getId(),
                        "agent", request.agentType().name(),
                        "scope", request.scope().name(),
                        "memoryType", request.memoryType().name()));

        if (traceService != null) {
            traceService.recordMemoryCreation(request.startupId(), null, request.agentType(),
                    request.memoryType().name(), request.scope().name(), request.importance(),
                    request.source(), saved.getId());
        }

        return MemoryRecord.from(saved);
    }

    // ---- Retrieval (deterministic, scope-explicit) --------------------------

    /** One agent's private memories, newest first. Never returns other agents' or shared memories. */
    @Transactional(readOnly = true)
    public List<MemoryRecord> findForAgent(Long startupId, AgentType agentType) {
        if (startupId == null) {
            log.warn("Memory retrieval rejected: null startupId.");
            return List.of();
        }
        if (agentType == null) {
            retrievalFailed(startupId, "An agent-specific memory retrieval requires a non-null agent.");
            return List.of();
        }
        List<MemoryRecord> records = project(repository.findByStartupIdAndScopeAndAgentTypeOrderByCreatedAtDescIdDesc(
                startupId, MemoryScope.AGENT_PRIVATE, agentType));
        if (traceService != null) {
            traceService.recordMemoryRetrieval(startupId, null, agentType, MemoryScope.AGENT_PRIVATE.name(), records.size(), "Private agent memory retrieval");
        }
        return records;
    }

    /** The startup's shared memories (the one canonical row per shared memory), newest first. */
    @Transactional(readOnly = true)
    public List<MemoryRecord> findShared(Long startupId) {
        if (startupId == null) {
            log.warn("Memory retrieval rejected: null startupId.");
            return List.of();
        }
        List<MemoryRecord> records = project(repository.findByStartupIdAndScopeOrderByCreatedAtDescIdDesc(
                startupId, MemoryScope.STARTUP_SHARED));
        if (traceService != null) {
            traceService.recordMemoryRetrieval(startupId, null, null, MemoryScope.STARTUP_SHARED.name(), records.size(), "Startup shared memory retrieval");
        }
        return records;
    }

    /** Recent memories for a startup across all scopes/agents, newest first, bounded. */
    @Transactional(readOnly = true)
    public List<MemoryRecord> findRecent(Long startupId, int limit) {
        if (startupId == null) {
            log.warn("Memory retrieval rejected: null startupId.");
            return List.of();
        }
        int bounded = limit <= 0 ? DEFAULT_RECENT_LIMIT : limit;
        return project(repository.findByStartupIdOrderByCreatedAtDescIdDesc(
                startupId, PageRequest.of(0, bounded)));
    }

    /** Memory-type filtered retrieval for a startup, newest first. */
    @Transactional(readOnly = true)
    public List<MemoryRecord> findByType(Long startupId, MemoryType memoryType) {
        if (startupId == null) {
            log.warn("Memory retrieval rejected: null startupId.");
            return List.of();
        }
        if (memoryType == null) {
            retrievalFailed(startupId, "A memory-type retrieval requires a non-null memoryType.");
            return List.of();
        }
        return project(repository.findByStartupIdAndMemoryTypeOrderByCreatedAtDescIdDesc(
                startupId, memoryType));
    }

    /** Typed dispatch over {@link MemoryQuery} (requirement 19). */
    @Transactional(readOnly = true)
    public List<MemoryRecord> find(MemoryQuery query) {
        if (query == null || query.kind() == null) {
            return List.of();
        }
        return switch (query.kind()) {
            case FOR_AGENT -> findForAgent(query.startupId(), query.agentType());
            case SHARED -> findShared(query.startupId());
            case RECENT -> findRecent(query.startupId(), query.limit());
            case BY_TYPE -> findByType(query.startupId(), query.memoryType());
        };
    }

    // ---- Validation ---------------------------------------------------------

    private void validate(CreateMemoryRequest r) {
        if (r == null) {
            throw new MemoryValidationException("REQUEST_REQUIRED", "A memory request is required.");
        }
        if (r.startupId() == null) {
            throw new MemoryValidationException("STARTUP_REQUIRED", "A memory must belong to a startup.");
        }
        if (r.agentType() == null) {
            throw new MemoryValidationException("AGENT_REQUIRED", "A memory must have an authoring agent.");
        }
        if (r.scope() == null) {
            throw new MemoryValidationException("SCOPE_REQUIRED", "A memory must declare a scope.");
        }
        if (r.memoryType() == null) {
            throw new MemoryValidationException("TYPE_REQUIRED", "A memory must declare a memory type.");
        }
        String content = r.normalizedContent();
        if (content == null || content.isEmpty()) {
            throw new MemoryValidationException("CONTENT_REQUIRED", "A memory must have non-empty content.");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new MemoryValidationException("CONTENT_TOO_LARGE",
                    "Memory content exceeds " + MAX_CONTENT_LENGTH + " characters.");
        }
        if (r.importance() < MIN_IMPORTANCE || r.importance() > MAX_IMPORTANCE) {
            throw new MemoryValidationException("IMPORTANCE_OUT_OF_RANGE",
                    "Importance must be between " + MIN_IMPORTANCE + " and " + MAX_IMPORTANCE + ".");
        }
    }

    /** A retrieval keyed to a real startup can emit a controlled failure event. */
    private void retrievalFailed(Long startupId, String reason) {
        log.warn("MEMORY_RETRIEVAL_FAILED startup={} reason={}", startupId, reason);
        eventService.record(startupId, EventType.MEMORY_RETRIEVAL_FAILED, reason, Map.of());
    }

    private static List<MemoryRecord> project(List<AgentMemory> rows) {
        return rows.stream().map(MemoryRecord::from).toList();
    }
}
