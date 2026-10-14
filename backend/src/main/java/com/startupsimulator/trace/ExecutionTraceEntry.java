package com.startupsimulator.trace;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Persisted execution trace record capturing real-time runtime agentic actions,
 * decisions, messaging, tool calls, memory activity, replanning, and recovery.
 */
@Entity
@Table(name = "execution_trace_entries", indexes = {
        @Index(name = "idx_trace_startup", columnList = "startup_id"),
        @Index(name = "idx_trace_run", columnList = "trace_id")
})
@Getter
@Setter
@NoArgsConstructor
public class ExecutionTraceEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "startup_id", nullable = false)
    private Long startupId;

    @Column(name = "trace_id", nullable = false, length = 64)
    private String traceId;

    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    @Column(nullable = false, updatable = false)
    private Instant timestamp = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ExecutionTraceType type;

    @Column(length = 50)
    private String agent;

    @Column(length = 50)
    private String sender;

    @Column(length = 50)
    private String recipient;

    @Column(length = 100)
    private String action;

    @Column(columnDefinition = "TEXT")
    private String decision;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(length = 100)
    private String toolName;

    @Column(columnDefinition = "TEXT")
    private String toolArguments;

    @Column(columnDefinition = "TEXT")
    private String toolResult;

    @Column(columnDefinition = "TEXT")
    private String outcome;

    @Column(columnDefinition = "TEXT")
    private String payload;
}
