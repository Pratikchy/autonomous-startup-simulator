package com.startupsimulator.model;

import com.startupsimulator.model.enums.AgentType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "app_change_requests", indexes = {
        @Index(name = "idx_change_request_startup", columnList = "startup_id")
})
@Getter
@Setter
@NoArgsConstructor
public class AppChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "startup_id", nullable = false)
    private Long startupId;

    @Column(name = "user_prompt", nullable = false, columnDefinition = "TEXT")
    private String userPrompt;

    @Enumerated(EnumType.STRING)
    @Column(name = "agent_type", length = 32)
    private AgentType agentType;

    @Column(name = "agent_name", length = 64)
    private String agentName;

    /**
     * PLANNING, PROPOSED, APPROVED, APPLYING, SUCCEEDED, FAILED, REJECTED
     */
    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "plan_summary", columnDefinition = "TEXT")
    private String planSummary;

    /**
     * Serialized JSON array of ProposedFileChange:
     * [{"relativePath":"src/App.tsx","action":"MODIFY","description":"...","content":"..."}]
     */
    @Column(name = "proposed_changes_json", columnDefinition = "TEXT")
    private String proposedChangesJson;

    @Column(name = "backup_snapshot_dir", length = 512)
    private String backupSnapshotDir;

    @Column(name = "build_output", columnDefinition = "TEXT")
    private String buildOutput;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
