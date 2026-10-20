package com.startupsimulator.model;

import com.startupsimulator.model.enums.SimulationPhase;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Aggregate root for a virtual startup. This entity <b>is the source of truth</b>
 * for the shared startup narrative and health metrics — the frontend never owns
 * this state. The evolving narrative fields (problem, solution, ...) together
 * form the "StartupContext" that every agent reads from and writes to; the
 * runtime view of that context is assembled by
 * {@link com.startupsimulator.service.StartupContextService}.
 */
@Entity
@Table(name = "startups")
@Getter
@Setter
@NoArgsConstructor
public class Startup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "original_idea", nullable = false, columnDefinition = "TEXT")
    private String originalIdea;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SimulationPhase currentPhase = SimulationPhase.IDEA;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    // ---- Shared narrative (StartupContext) — filled in by the agents --------
    @Column(columnDefinition = "TEXT")
    private String problem;

    @Column(columnDefinition = "TEXT")
    private String solution;

    @Column(name = "target_audience", columnDefinition = "TEXT")
    private String targetAudience;

    @Column(name = "value_proposition", columnDefinition = "TEXT")
    private String valueProposition;

    @Column(name = "business_model", columnDefinition = "TEXT")
    private String businessModel;

    @Column(columnDefinition = "TEXT")
    private String constraints;

    /** Newline-separated risks surfaced by the departments. */
    @Column(columnDefinition = "TEXT")
    private String risks;

    // ---- CEO strategic analysis (Phase 2A, LLM-produced) --------------------
    /** CEO's 2-3 sentence strategic summary of the venture. */
    @Column(name = "executive_summary", columnDefinition = "TEXT")
    private String executiveSummary;

    /** Newline-separated strategic priorities set by the CEO. */
    @Column(name = "strategic_objectives", columnDefinition = "TEXT")
    private String strategicObjectives;

    /** Newline-separated key assumptions the plan depends on. */
    @Column(columnDefinition = "TEXT")
    private String assumptions;

    /** CEO's recommended focused MVP direction. */
    @Column(name = "mvp_direction", columnDefinition = "TEXT")
    private String mvpDirection;

    /** Provider that produced the CEO analysis ("mock" or "openai"). */
    @Column(name = "ceo_analysis_provider", length = 32)
    private String ceoAnalysisProvider;

    /** True when the last real CEO analysis attempt failed (fallback in use). */
    @Column(name = "ceo_analysis_failed")
    private boolean ceoAnalysisFailed = false;

    /** Short, non-sensitive description of the last CEO analysis failure. */
    @Column(name = "ceo_analysis_error", columnDefinition = "TEXT")
    private String ceoAnalysisError;

    // ---- Startup health metrics (0-100 unless noted) ------------------------
    private int overallProgress = 0;
    private int mvpProgress = 0;
    private int technicalFeasibility = 0;
    private int marketReadiness = 0;
    private int financialHealth = 0;

    /** Remaining cash, in whole currency units. */
    private double budgetRemaining = 0d;

    /** Estimated runway in months. */
    private double runwayMonths = 0d;

    private boolean simulationStarted = false;
    private boolean simulationCompleted = false;

    /** Persisted simulation clock — survives backend restarts. */
    @Column(name = "execution_day")
    private int executionDay = 1;

    /** True while the Phase-2F execution engine is actively advancing tasks. */
    @Column(name = "execution_running")
    private boolean executionRunning = false;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    public Startup(String name, String originalIdea) {
        this.name = name;
        this.originalIdea = originalIdea;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }
}
