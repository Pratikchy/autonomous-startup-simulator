package com.startupsimulator.generator.spec;

import java.util.List;

public record ScreenSpec(
        String id,
        String title,
        String icon,
        String description,
        List<String> keyFeatures
) {}
