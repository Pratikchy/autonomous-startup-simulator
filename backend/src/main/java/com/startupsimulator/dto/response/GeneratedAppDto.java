package com.startupsimulator.dto.response;

import com.startupsimulator.model.GeneratedApp;

import java.time.Instant;

public record GeneratedAppDto(
        Long id,
        Long startupId,
        String appName,
        String template,
        String status,
        String buildStatus,
        String buildOutput,
        String productSpecJson,
        Integer previewPort,
        String previewUrl,
        Instant createdAt,
        Instant updatedAt
) {
    public static GeneratedAppDto fromEntity(GeneratedApp app) {
        if (app == null) return null;
        return new GeneratedAppDto(
                app.getId(),
                app.getStartupId(),
                app.getAppName(),
                app.getTemplate(),
                app.getStatus(),
                app.getBuildStatus(),
                app.getBuildOutput(),
                app.getProductSpecJson(),
                app.getPreviewPort(),
                app.getPreviewUrl(),
                app.getCreatedAt(),
                app.getUpdatedAt()
        );
    }

    public static GeneratedAppDto ungenerated(Long startupId) {
        return new GeneratedAppDto(
                null, startupId, null, "REACT_VITE_TS", "UNGENERATED", "NOT_BUILT",
                null, null, null, null, null, null
        );
    }
}
