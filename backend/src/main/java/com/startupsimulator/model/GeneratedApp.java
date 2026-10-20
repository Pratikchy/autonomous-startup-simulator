package com.startupsimulator.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Persisted record of a generated standalone web application for a virtual startup.
 * Stored in H2/PostgreSQL so workspace metadata, build logs, and preview port
 * survive backend restarts.
 */
@Entity
@Table(name = "generated_apps")
@Getter
@Setter
@NoArgsConstructor
public class GeneratedApp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "startup_id", nullable = false, unique = true)
    private Long startupId;

    @Column(name = "app_name", nullable = false)
    private String appName;

    @Column(nullable = false, length = 64)
    private String template = "REACT_VITE_TS";

    /** Lifecycle: UNGENERATED, GENERATING, GENERATED, BUILDING, READY, FAILED */
    @Column(nullable = false, length = 32)
    private String status = "UNGENERATED";

    /** Build Status: NOT_BUILT, SUCCESS, FAILED */
    @Column(name = "build_status", nullable = false, length = 32)
    private String buildStatus = "NOT_BUILT";

    @Column(name = "build_output", columnDefinition = "TEXT")
    private String buildOutput;

    @Column(name = "product_spec_json", columnDefinition = "TEXT")
    private String productSpecJson;

    @Column(name = "preview_port")
    private Integer previewPort;

    @Column(name = "preview_url")
    private String previewUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
