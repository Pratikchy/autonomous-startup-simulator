package com.startupsimulator.generator.spec;

import java.util.List;

public record ProductSpec(
        String appName,
        String category,
        String tagline,
        String summary,
        List<String> targetUsers,
        List<String> primaryWorkflows,
        List<ScreenSpec> screens,
        List<String> reusableComponents,
        DesignSystemSpec designSystem,
        List<String> dataRequirements,
        List<String> acceptanceCriteria,
        String simulatedNotice
) {}
